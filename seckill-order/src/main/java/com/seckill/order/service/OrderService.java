package com.seckill.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seckill.common.config.SeckillFeatureProperties;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.mq.OrderCreateMessage;
import com.seckill.common.mq.OrderExpireMessage;
import com.seckill.common.mq.OrderMqConstants;
import com.seckill.common.redis.StockRollbackHelper;
import com.seckill.common.result.ResultCode;
import com.seckill.order.config.OrderProperties;
import com.seckill.order.domain.SeckillOrder;
import com.seckill.order.dto.OrderView;
import com.seckill.order.mapper.OrderMapper;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final String ROLE_ADMIN = "ADMIN";

    private final OrderMapper orderMapper;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplate;
    private final OrderProperties orderProperties;
    private final SeckillFeatureProperties featureProperties;

    public OrderService(
            OrderMapper orderMapper,
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate stringRedisTemplate,
            ObjectProvider<RocketMQTemplate> rocketMQTemplate,
            OrderProperties orderProperties,
            SeckillFeatureProperties featureProperties
    ) {
        this.orderMapper = orderMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.rocketMQTemplate = rocketMQTemplate;
        this.orderProperties = orderProperties;
        this.featureProperties = featureProperties;
    }

    /**
     * MQ 幂等建单。失败时回滚 Redis 库存+已购（不重试，避免重复回滚竞态）。
     */
    public void createFromMessage(OrderCreateMessage message) {
        if (message == null || message.orderToken() == null || message.orderToken().isBlank()) {
            log.warn("ignore invalid order message: {}", message);
            return;
        }
        SeckillOrder existing = findByOrderNo(message.orderToken());
        if (existing != null) {
            log.info("idempotent skip, order exists: {}", message.orderToken());
            return;
        }

        Integer amountFen = loadActivityPriceFen(message.activityId());
        if (amountFen == null) {
            log.error("activity missing, rollback stock. activityId={} token={}",
                    message.activityId(), message.orderToken());
            StockRollbackHelper.rollback(stringRedisTemplate, message.activityId(), message.userId());
            throw new IllegalStateException("activity missing for order token=" + message.orderToken());
        }

        LocalDateTime createdAt = message.createdAt() == null
                ? LocalDateTime.now(ZONE)
                : LocalDateTime.ofInstant(message.createdAt(), ZONE);
        LocalDateTime expireAt = createdAt.plusMinutes(orderProperties.expireMinutes());

        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(message.orderToken());
        order.setUserId(message.userId());
        order.setActivityId(message.activityId());
        order.setAmountFen(amountFen);
        order.setStatus(SeckillOrder.STATUS_CREATED);
        order.setCreatedAt(createdAt);
        order.setExpireAt(expireAt);
        try {
            orderMapper.insert(order);
            scheduleExpire(order.getOrderNo(), orderProperties.expireMinutes());
            log.info("order created: orderNo={} expireAt={}", order.getOrderNo(), expireAt);
        } catch (DuplicateKeyException dup) {
            log.info("idempotent duplicate key: {}", message.orderToken());
        } catch (RuntimeException ex) {
            log.error("persist order failed, rollback stock. token={}", message.orderToken(), ex);
            StockRollbackHelper.rollback(stringRedisTemplate, message.activityId(), message.userId());
            throw ex;
        }
    }

    public List<OrderView> list(long userId, String role) {
        LambdaQueryWrapper<SeckillOrder> qw = new LambdaQueryWrapper<SeckillOrder>()
                .orderByDesc(SeckillOrder::getId);
        if (!ROLE_ADMIN.equals(role)) {
            qw.eq(SeckillOrder::getUserId, userId);
        }
        return orderMapper.selectList(qw).stream().map(this::toView).toList();
    }

    public OrderView detail(long userId, String role, String orderNo) {
        return toView(requireOwned(userId, role, orderNo));
    }

    /** Mock 支付：固定成功。 */
    @Transactional
    public OrderView pay(long userId, String role, String orderNo) {
        SeckillOrder order = requireOwned(userId, role, orderNo);
        if (SeckillOrder.STATUS_PAID.equals(order.getStatus())) {
            return toView(order);
        }
        if (SeckillOrder.STATUS_EXPIRED.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "订单已过期，无法支付");
        }
        if (!SeckillOrder.STATUS_CREATED.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前状态不可支付");
        }
        if (isPastExpire(order)) {
            expireIfCreated(order.getOrderNo());
            throw new BusinessException(ResultCode.BAD_REQUEST, "订单已过期，无法支付");
        }
        order.setStatus(SeckillOrder.STATUS_PAID);
        orderMapper.updateById(order);
        return toView(order);
    }

    /** 取消未支付订单并回滚 Redis。 */
    @Transactional
    public OrderView cancel(long userId, String role, String orderNo) {
        SeckillOrder order = requireOwned(userId, role, orderNo);
        if (SeckillOrder.STATUS_CANCELLED.equals(order.getStatus())
                || SeckillOrder.STATUS_EXPIRED.equals(order.getStatus())) {
            return toView(order);
        }
        if (!SeckillOrder.STATUS_CREATED.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅未支付订单可取消");
        }
        boolean closed = closeCreatedOrder(order.getOrderNo(), SeckillOrder.STATUS_CANCELLED);
        if (closed) {
            StockRollbackHelper.rollback(stringRedisTemplate, order.getActivityId(), order.getUserId());
        }
        return toView(requireOwned(userId, role, orderNo));
    }

    /** 延迟队列到期：仍待支付则过期并回滚。 */
    @Transactional
    public void expireFromMessage(OrderExpireMessage message) {
        if (message == null || message.orderNo() == null || message.orderNo().isBlank()) {
            return;
        }
        expireIfCreated(message.orderNo());
    }

    /** 扫表兜底：关闭已过 expire_at 的 CREATED 订单。 */
    public int expireOverdueBatch(int limit) {
        int batch = Math.max(1, Math.min(limit, 200));
        LocalDateTime now = LocalDateTime.now(ZONE);
        List<SeckillOrder> overdue = orderMapper.selectList(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getStatus, SeckillOrder.STATUS_CREATED)
                        .isNotNull(SeckillOrder::getExpireAt)
                        .lt(SeckillOrder::getExpireAt, now)
                        .orderByAsc(SeckillOrder::getExpireAt)
                        .last("LIMIT " + batch)
        );
        int closed = 0;
        for (SeckillOrder order : overdue) {
            expireIfCreated(order.getOrderNo());
            closed++;
        }
        return closed;
    }

    private void expireIfCreated(String orderNo) {
        SeckillOrder order = findByOrderNo(orderNo);
        if (order == null) {
            return;
        }
        boolean closed = closeCreatedOrder(orderNo, SeckillOrder.STATUS_EXPIRED);
        if (closed) {
            StockRollbackHelper.rollback(stringRedisTemplate, order.getActivityId(), order.getUserId());
            log.info("order expired and stock rolled back: {}", orderNo);
        }
    }

    /** 条件更新，保证支付/取消/过期互斥，避免双重回滚。 */
    private boolean closeCreatedOrder(String orderNo, String targetStatus) {
        return orderMapper.update(
                null,
                new LambdaUpdateWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getOrderNo, orderNo)
                        .eq(SeckillOrder::getStatus, SeckillOrder.STATUS_CREATED)
                        .set(SeckillOrder::getStatus, targetStatus)
        ) == 1;
    }

    private void scheduleExpire(String orderNo, int expireMinutes) {
        if (!featureProperties.mqEnabled()) {
            return;
        }
        RocketMQTemplate template = rocketMQTemplate.getIfAvailable();
        if (template == null) {
            log.warn("RocketMQTemplate missing, skip expire schedule. orderNo={}", orderNo);
            return;
        }
        long ttlMs = Math.max(TimeUnit.MINUTES.toMillis(expireMinutes), 1000L);
        try {
            // 默认支付时限 3min 时走 delayLevel=7；其它时长用 Timer 延迟（需 broker timerWheelEnable）
            SendResult result;
            if (expireMinutes == 3) {
                result = template.syncSend(
                        OrderMqConstants.TOPIC_EXPIRE,
                        MessageBuilder.withPayload(new OrderExpireMessage(orderNo)).build(),
                        5000,
                        OrderMqConstants.DELAY_LEVEL_3_MIN
                );
            } else {
                result = template.syncSendDelayTimeMills(
                        OrderMqConstants.TOPIC_EXPIRE,
                        MessageBuilder.withPayload(new OrderExpireMessage(orderNo)).build(),
                        ttlMs
                );
            }
            if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException("rocketmq delay send failed: " + result);
            }
        } catch (RuntimeException ex) {
            log.warn("schedule order expire failed, scan job will cover. orderNo={}", orderNo, ex);
        }
    }

    private boolean isPastExpire(SeckillOrder order) {
        return order.getExpireAt() != null && LocalDateTime.now(ZONE).isAfter(order.getExpireAt());
    }

    private SeckillOrder requireOwned(long userId, String role, String orderNo) {
        SeckillOrder order = findByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "订单不存在");
        }
        if (!ROLE_ADMIN.equals(role) && !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该订单");
        }
        return order;
    }

    private SeckillOrder findByOrderNo(String orderNo) {
        return orderMapper.selectOne(
                new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getOrderNo, orderNo)
        );
    }

    private Integer loadActivityPriceFen(long activityId) {
        List<Integer> rows = jdbcTemplate.query(
                "SELECT price_fen FROM t_activity WHERE id = ?",
                (rs, rowNum) -> rs.getInt("price_fen"),
                activityId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String loadActivityTitle(long activityId) {
        List<String> rows = jdbcTemplate.query(
                "SELECT title FROM t_activity WHERE id = ?",
                (rs, rowNum) -> rs.getString("title"),
                activityId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private OrderView toView(SeckillOrder order) {
        Instant createdAt = order.getCreatedAt() == null
                ? null
                : order.getCreatedAt().atZone(ZONE).toInstant();
        Instant expireAt = order.getExpireAt() == null
                ? null
                : order.getExpireAt().atZone(ZONE).toInstant();
        return new OrderView(
                order.getOrderNo(),
                order.getUserId(),
                order.getActivityId(),
                loadActivityTitle(order.getActivityId()),
                order.getStatus(),
                order.getAmountFen(),
                createdAt,
                expireAt
        );
    }
}
