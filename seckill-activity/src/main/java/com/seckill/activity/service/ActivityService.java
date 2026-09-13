package com.seckill.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.activity.domain.Activity;
import com.seckill.activity.dto.ActivityCreateRequest;
import com.seckill.activity.dto.ActivityUpdateRequest;
import com.seckill.activity.dto.ActivityView;
import com.seckill.activity.mapper.ActivityMapper;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.mq.ActivityExpireMessage;
import com.seckill.common.mq.ActivityMqConstants;
import com.seckill.common.redis.SeckillRedisKeys;
import com.seckill.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Service
public class ActivityService {

    public static final String ROLE_ADMIN = "ADMIN";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    private final ActivityMapper activityMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;

    public ActivityService(
            ActivityMapper activityMapper,
            StringRedisTemplate stringRedisTemplate,
            RabbitTemplate rabbitTemplate
    ) {
        this.activityMapper = activityMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.rabbitTemplate = rabbitTemplate;
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
        Activity entity = new Activity();
        entity.setTitle(request.title().trim());
        entity.setPriceFen(request.priceFen());
        entity.setOriginPriceFen(request.originPriceFen());
        entity.setStock(request.stock());
        entity.setStatus(Activity.STATUS_CLOSED);
        entity.setStartAt(toLocal(request.startAt()));
        entity.setEndAt(toLocal(request.endAt()));
        activityMapper.insert(entity);
        return toView(entity);
    }

    public ActivityView update(String role, long id, ActivityUpdateRequest request) {
        requireAdmin(role);
        validateTimeRange(request.startAt(), request.endAt());
        Activity entity = requireActivity(id);
        if (isOpen(entity)) {
            assertOpenImmutableFields(entity, request);
            entity.setTitle(request.title().trim());
            activityMapper.updateById(entity);
            return toView(entity);
        }
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先关闭活动再删除");
        }
        activityMapper.deleteById(id);
        stringRedisTemplate.delete(SeckillRedisKeys.stock(id));
        stringRedisTemplate.delete(SeckillRedisKeys.open(id));
    }

    public ActivityView open(String role, long id) {
        requireAdmin(role);
        Activity entity = requireActivity(id);
        String key = SeckillRedisKeys.stock(id);
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先预热 Redis 库存再开抢");
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
        return doClose(requireActivity(id));
    }

    /** 延迟队列到期：仍开抢则自动关闭。 */
    public void expireIfOpen(long activityId) {
        Activity entity = activityMapper.selectById(activityId);
        if (entity == null || !isOpen(entity)) {
            return;
        }
        doClose(entity);
        log.info("activity auto-closed by expire: id={}", activityId);
    }

    private ActivityView doClose(Activity entity) {
        entity.setStatus(Activity.STATUS_CLOSED);
        activityMapper.updateById(entity);
        stringRedisTemplate.delete(SeckillRedisKeys.open(entity.getId()));
        return toView(entity);
    }

    private void scheduleExpire(long activityId, long delayMs) {
        long ttl = Math.max(delayMs, 1000L);
        rabbitTemplate.convertAndSend(
                ActivityMqConstants.EXCHANGE,
                ActivityMqConstants.ROUTING_KEY_DELAY,
                new ActivityExpireMessage(activityId),
                msg -> {
                    msg.getMessageProperties().setExpiration(String.valueOf(ttl));
                    return msg;
                }
        );
    }

    /** 将 DB 配置库存写入 Redis（覆盖）。 */
    public ActivityView preheat(String role, long id) {
        requireAdmin(role);
        Activity entity = requireActivity(id);
        if (isOpen(entity)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开抢中禁止预热，避免覆盖现场库存");
        }
        stringRedisTemplate.opsForValue().set(SeckillRedisKeys.stock(id), String.valueOf(entity.getStock()));
        return toView(entity);
    }

    /** B 端直接改 Redis 库存；不强制同步 DB。 */
    public ActivityView updateRedisStock(String role, long id, int stock) {
        requireAdmin(role);
        Activity entity = requireActivity(id);
        if (isOpen(entity)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开抢中禁止修改 Redis 库存");
        }
        String key = SeckillRedisKeys.stock(id);
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "尚未预热，请先预热再改 Redis 库存");
        }
        stringRedisTemplate.opsForValue().set(key, String.valueOf(stock));
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

    private static boolean isOpen(Activity entity) {
        return entity.getStatus() != null && entity.getStatus() == Activity.STATUS_OPEN;
    }

    /** 开抢后仅允许改标题；价格/库存/时间不可变。 */
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
        Integer redisStock = readRedisStock(entity.getId());
        return new ActivityView(
                entity.getId(),
                entity.getTitle(),
                entity.getPriceFen(),
                entity.getOriginPriceFen(),
                entity.getStock(),
                redisStock,
                entity.getStatus() != null && entity.getStatus() == Activity.STATUS_OPEN ? "OPEN" : "CLOSED",
                toInstant(entity.getStartAt()),
                toInstant(entity.getEndAt())
        );
    }

    private Integer readRedisStock(long activityId) {
        String raw = stringRedisTemplate.opsForValue().get(SeckillRedisKeys.stock(activityId));
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
