package com.seckill.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.mq.OrderCreateMessage;
import com.seckill.common.redis.StockRollbackHelper;
import com.seckill.common.result.ResultCode;
import com.seckill.order.domain.SeckillOrder;
import com.seckill.order.dto.OrderView;
import com.seckill.order.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final String ROLE_ADMIN = "ADMIN";

    private final OrderMapper orderMapper;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public OrderService(
            OrderMapper orderMapper,
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate stringRedisTemplate
    ) {
        this.orderMapper = orderMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
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
            return;
        }

        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(message.orderToken());
        order.setUserId(message.userId());
        order.setActivityId(message.activityId());
        order.setAmountFen(amountFen);
        order.setStatus(SeckillOrder.STATUS_CREATED);
        order.setCreatedAt(message.createdAt() == null
                ? LocalDateTime.now(ZONE)
                : LocalDateTime.ofInstant(message.createdAt(), ZONE));
        try {
            orderMapper.insert(order);
            log.info("order created: orderNo={} userId={} activityId={}",
                    order.getOrderNo(), order.getUserId(), order.getActivityId());
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
        if (!SeckillOrder.STATUS_CREATED.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前状态不可支付");
        }
        order.setStatus(SeckillOrder.STATUS_PAID);
        orderMapper.updateById(order);
        return toView(order);
    }

    /** 取消未支付订单并回滚 Redis。 */
    @Transactional
    public OrderView cancel(long userId, String role, String orderNo) {
        SeckillOrder order = requireOwned(userId, role, orderNo);
        if (SeckillOrder.STATUS_CANCELLED.equals(order.getStatus())) {
            return toView(order);
        }
        if (!SeckillOrder.STATUS_CREATED.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅未支付订单可取消");
        }
        order.setStatus(SeckillOrder.STATUS_CANCELLED);
        orderMapper.updateById(order);
        StockRollbackHelper.rollback(stringRedisTemplate, order.getActivityId(), order.getUserId());
        return toView(order);
    }

    private SeckillOrder requireOwned(long userId, String role, String orderNo) {
        SeckillOrder order = findByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "订单不存在");
        }
        if (!ROLE_ADMIN.equals(role) && !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
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

    private OrderView toView(SeckillOrder order) {
        Instant createdAt = order.getCreatedAt() == null
                ? null
                : order.getCreatedAt().atZone(ZONE).toInstant();
        return new OrderView(
                order.getOrderNo(),
                order.getUserId(),
                order.getActivityId(),
                order.getStatus(),
                order.getAmountFen(),
                createdAt
        );
    }
}
