package com.seckill.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.activity.domain.Activity;
import com.seckill.activity.dto.ActivityCreateRequest;
import com.seckill.activity.dto.ActivityUpdateRequest;
import com.seckill.activity.dto.ActivityView;
import com.seckill.activity.dto.StockReconcileView;
import com.seckill.activity.mapper.ActivityMapper;
import com.seckill.common.config.SeckillFeatureProperties;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.mq.ActivityExpireMessage;
import com.seckill.common.mq.ActivityMqConstants;
import com.seckill.common.redis.SeckillRedisKeys;
import com.seckill.common.result.ResultCode;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * 活动状态机：DRAFT → PREHEATED → OPEN → CLOSED（终态，同活动不复用）。
 */
@Service
public class ActivityService {

    public static final String ROLE_ADMIN = "ADMIN";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    private final ActivityMapper activityMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final SeckillFeatureProperties featureProperties;

    public ActivityService(
            ActivityMapper activityMapper,
            StringRedisTemplate stringRedisTemplate,
            ObjectProvider<RocketMQTemplate> rocketMQTemplate,
            JdbcTemplate jdbcTemplate,
            SeckillFeatureProperties featureProperties
    ) {
        this.activityMapper = activityMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.rocketMQTemplate = rocketMQTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.featureProperties = featureProperties;
    }

    public List<ActivityView> list() {
        return activityMapper.selectList(
                new LambdaQueryWrapper<Activity>().orderByDesc(Activity::getId)
        ).stream().map(this::toView).toList();
    }

    public ActivityView detail(long id) {
        return toView(requireActivity(id));
    }

    public ActivityView create(String role, ActivityCreateRequest request) {
        requireAdmin(role);
        validateTimeRange(request.startAt(), request.endAt());
        validatePrices(request.priceFen(), request.originPriceFen());
        Activity entity = new Activity();
        entity.setTitle(request.title().trim());
        entity.setPriceFen(request.priceFen());
        entity.setOriginPriceFen(request.originPriceFen());
        entity.setStock(request.stock());
        entity.setStatus(Activity.STATUS_DRAFT);
        entity.setStartAt(toLocal(request.startAt()));
        entity.setEndAt(toLocal(request.endAt()));
        activityMapper.insert(entity);
        return toView(entity);
    }

    public ActivityView update(String role, long id, ActivityUpdateRequest request) {
        requireAdmin(role);
        validateTimeRange(request.startAt(), request.endAt());
        validatePrices(request.priceFen(), request.originPriceFen());
        Activity entity = requireActivity(id);
        int status = statusOf(entity);
        if (status == Activity.STATUS_CLOSED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "活动已结束（终态），不可修改，请新建活动");
        }
        if (status == Activity.STATUS_OPEN) {
            assertOpenImmutableFields(entity, request);
            entity.setTitle(request.title().trim());
            activityMapper.updateById(entity);
            return toView(entity);
        }
        // DRAFT / PREHEATED：可改配置
        entity.setTitle(request.title().trim());
        entity.setPriceFen(request.priceFen());
        entity.setOriginPriceFen(request.originPriceFen());
        entity.setStock(request.stock());
        entity.setStartAt(toLocal(request.startAt()));
        entity.setEndAt(toLocal(request.endAt()));
        activityMapper.updateById(entity);
        return toView(entity);
    }

    public void delete(String role, long id) {
        requireAdmin(role);
        Activity entity = requireActivity(id);
        if (isOpen(entity)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开抢中不可删除，请先关闭");
        }
        activityMapper.deleteById(id);
        stringRedisTemplate.delete(SeckillRedisKeys.stock(id));
        stringRedisTemplate.delete(SeckillRedisKeys.open(id));
        stringRedisTemplate.delete(SeckillRedisKeys.stockInit(id));
    }

    public ActivityView open(String role, long id) {
        requireAdmin(role);
        Activity entity = requireActivity(id);
        if (statusOf(entity) == Activity.STATUS_CLOSED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "活动已结束（终态），不可再次开抢，请新建活动");
        }
        if (statusOf(entity) != Activity.STATUS_PREHEATED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先预热后再开抢");
        }
        String key = SeckillRedisKeys.stock(id);
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Redis 库存缺失，请重新预热");
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (entity.getEndAt() == null || !entity.getEndAt().isAfter(now)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "活动已到结束时间，无法开抢");
        }
        entity.setStatus(Activity.STATUS_OPEN);
        activityMapper.updateById(entity);
        stringRedisTemplate.opsForValue().set(SeckillRedisKeys.open(id), "1");
        scheduleExpire(id, Duration.between(now, entity.getEndAt()).toMillis());
        return toView(entity);
    }

    public ActivityView close(String role, long id) {
        requireAdmin(role);
        Activity entity = requireActivity(id);
        if (statusOf(entity) == Activity.STATUS_CLOSED) {
            return toView(entity);
        }
        if (!isOpen(entity)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅开抢中的活动可关闭；关闭后为终态，须新建活动再开抢");
        }
        return doClose(entity);
    }

    /** 延迟队列到期：仍开抢则自动关闭为终态。 */
    public void expireIfOpen(long activityId) {
        Activity entity = activityMapper.selectById(activityId);
        if (entity == null || !isOpen(entity)) {
            return;
        }
        doClose(entity);
        log.info("activity auto-closed by expire: id={}", activityId);
    }

    /** 扫表兜底：关闭已过 end_at 仍 OPEN 的活动。 */
    public int closeOverdueBatch(int limit) {
        int batch = Math.max(1, Math.min(limit, 100));
        LocalDateTime now = LocalDateTime.now(ZONE);
        List<Activity> overdue = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getStatus, Activity.STATUS_OPEN)
                        .isNotNull(Activity::getEndAt)
                        .lt(Activity::getEndAt, now)
                        .orderByAsc(Activity::getEndAt)
                        .last("LIMIT " + batch)
        );
        int closed = 0;
        for (Activity entity : overdue) {
            doClose(entity);
            closed++;
            log.info("activity closed by scan: id={}", entity.getId());
        }
        return closed;
    }

    /** 库存对账：init ≈ redis + CREATED + PAID。 */
    public StockReconcileView reconcile(String role, long id) {
        requireAdmin(role);
        Activity entity = requireActivity(id);
        Integer redisStock = readRedisStock(id);
        Integer initStock = readInt(SeckillRedisKeys.stockInit(id));
        long created = countOrders(id, "CREATED");
        long paid = countOrders(id, "PAID");
        long cancelled = countOrders(id, "CANCELLED");
        long expired = countOrders(id, "EXPIRED");
        long occupied = created + paid;
        Integer expectedRedis = initStock == null ? null : initStock - (int) occupied;
        boolean consistent;
        String message;
        if (initStock == null || redisStock == null) {
            consistent = false;
            message = "缺少 init 或 redis 库存（请先预热；旧活动需重新预热才会写入 init）";
        } else if (expectedRedis != null && expectedRedis.equals(redisStock)) {
            consistent = true;
            message = "一致：init = redis + CREATED + PAID";
        } else {
            consistent = false;
            message = "不一致：期望 redis=" + expectedRedis + " 实际=" + redisStock
                    + "（可能超卖/漏回滚，或预热后又改过基准）";
        }
        return new StockReconcileView(
                id,
                statusName(statusOf(entity)),
                entity.getStock() == null ? 0 : entity.getStock(),
                redisStock,
                initStock,
                created,
                paid,
                cancelled,
                expired,
                occupied,
                expectedRedis,
                consistent,
                message
        );
    }

    private long countOrders(long activityId, String status) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_order WHERE activity_id = ? AND status = ?",
                Long.class,
                activityId,
                status
        );
        return n == null ? 0L : n;
    }

    private ActivityView doClose(Activity entity) {
        entity.setStatus(Activity.STATUS_CLOSED);
        activityMapper.updateById(entity);
        stringRedisTemplate.delete(SeckillRedisKeys.open(entity.getId()));
        return toView(entity);
    }

    private void scheduleExpire(long activityId, long delayMs) {
        if (!featureProperties.mqEnabled()) {
            return;
        }
        RocketMQTemplate template = rocketMQTemplate.getIfAvailable();
        if (template == null) {
            log.warn("RocketMQTemplate missing, skip activity expire schedule. id={}", activityId);
            return;
        }
        long ttl = Math.max(delayMs, 1000L);
        try {
            SendResult result = template.syncSendDelayTimeMills(
                    ActivityMqConstants.TOPIC_EXPIRE,
                    MessageBuilder.withPayload(new ActivityExpireMessage(activityId)).build(),
                    ttl
            );
            if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException("rocketmq delay send failed: " + result);
            }
        } catch (RuntimeException ex) {
            log.warn("schedule activity expire failed, scan job will cover. id={}", activityId, ex);
        }
    }

    /** DRAFT/PREHEATED → PREHEATED：将 DB 配置库存写入 Redis。 */
    public ActivityView preheat(String role, long id) {
        requireAdmin(role);
        Activity entity = requireActivity(id);
        int status = statusOf(entity);
        if (status == Activity.STATUS_OPEN) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开抢中禁止预热，避免覆盖现场库存");
        }
        if (status == Activity.STATUS_CLOSED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "活动已结束（终态），不可预热，请新建活动");
        }
        String stockVal = String.valueOf(entity.getStock());
        stringRedisTemplate.opsForValue().set(SeckillRedisKeys.stock(id), stockVal);
        stringRedisTemplate.opsForValue().set(SeckillRedisKeys.stockInit(id), stockVal);
        entity.setStatus(Activity.STATUS_PREHEATED);
        activityMapper.updateById(entity);
        return toView(entity);
    }

    /** 仅 PREHEATED 可改 Redis 库存。 */
    public ActivityView updateRedisStock(String role, long id, int stock) {
        requireAdmin(role);
        Activity entity = requireActivity(id);
        int status = statusOf(entity);
        if (status == Activity.STATUS_OPEN) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开抢中禁止修改 Redis 库存");
        }
        if (status == Activity.STATUS_CLOSED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "活动已结束（终态），不可改库存");
        }
        if (status != Activity.STATUS_PREHEATED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先预热后再改 Redis 库存");
        }
        String key = SeckillRedisKeys.stock(id);
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Redis 库存缺失，请重新预热");
        }
        String stockVal = String.valueOf(stock);
        stringRedisTemplate.opsForValue().set(key, stockVal);
        stringRedisTemplate.opsForValue().set(SeckillRedisKeys.stockInit(id), stockVal);
        return toView(requireActivity(id));
    }

    private Activity requireActivity(long id) {
        Activity entity = activityMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "活动不存在");
        }
        return entity;
    }

    private void requireAdmin(String role) {
        if (!ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "需要管理员权限");
        }
    }

    private void validateTimeRange(Instant startAt, Instant endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "结束时间须晚于开始时间");
        }
    }

    private void validatePrices(int priceFen, int originPriceFen) {
        if (priceFen > originPriceFen) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "秒杀价不能高于原价");
        }
    }

    private static int statusOf(Activity entity) {
        return entity.getStatus() == null ? Activity.STATUS_DRAFT : entity.getStatus();
    }

    private static boolean isOpen(Activity entity) {
        return statusOf(entity) == Activity.STATUS_OPEN;
    }

    private void assertOpenImmutableFields(Activity entity, ActivityUpdateRequest request) {
        if (!Objects.equals(entity.getPriceFen(), request.priceFen())
                || !Objects.equals(entity.getOriginPriceFen(), request.originPriceFen())
                || !Objects.equals(entity.getStock(), request.stock())
                || !sameWallTime(entity.getStartAt(), request.startAt())
                || !sameWallTime(entity.getEndAt(), request.endAt())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开抢中仅允许修改标题，价格/库存/时间不可改");
        }
    }

    private static boolean sameWallTime(LocalDateTime db, Instant request) {
        if (db == null || request == null) {
            return false;
        }
        return db.truncatedTo(ChronoUnit.SECONDS).equals(toLocal(request).truncatedTo(ChronoUnit.SECONDS));
    }

    private ActivityView toView(Activity entity) {
        return new ActivityView(
                entity.getId(),
                entity.getTitle(),
                entity.getPriceFen(),
                entity.getOriginPriceFen(),
                entity.getStock(),
                readRedisStock(entity.getId()),
                statusName(statusOf(entity)),
                toInstant(entity.getStartAt()),
                toInstant(entity.getEndAt())
        );
    }

    private static String statusName(int status) {
        return switch (status) {
            case Activity.STATUS_OPEN -> "OPEN";
            case Activity.STATUS_PREHEATED -> "PREHEATED";
            case Activity.STATUS_CLOSED -> "CLOSED";
            default -> "DRAFT";
        };
    }

    private Integer readRedisStock(long activityId) {
        return readInt(SeckillRedisKeys.stock(activityId));
    }

    private Integer readInt(String key) {
        String raw = stringRedisTemplate.opsForValue().get(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZONE);
    }

    private static Instant toInstant(LocalDateTime time) {
        return time.atZone(ZONE).toInstant();
    }
}
