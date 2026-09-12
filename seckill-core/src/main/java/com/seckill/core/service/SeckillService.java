package com.seckill.core.service;

import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.ResultCode;
import com.seckill.core.mq.OrderCreateMessage;
import com.seckill.core.mq.OrderMqConstants;
import com.seckill.core.redis.StockLuaExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class SeckillService {

    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);

    private final StockLuaExecutor stockLuaExecutor;
    private final RabbitTemplate rabbitTemplate;

    public SeckillService(StockLuaExecutor stockLuaExecutor, RabbitTemplate rabbitTemplate) {
        this.stockLuaExecutor = stockLuaExecutor;
        this.rabbitTemplate = rabbitTemplate;
    }

    public Map<String, Object> grab(long activityId, long userId) {
        long remain = stockLuaExecutor.deduct(activityId, userId);
        if (remain == -3) {
            throw new BusinessException(ResultCode.NOT_STARTED);
        }
        if (remain == -1) {
            throw new BusinessException(ResultCode.DUPLICATE);
        }
        if (remain < 0) {
            throw new BusinessException(ResultCode.SOLD_OUT);
        }

        String orderToken = UUID.randomUUID().toString().replace("-", "");
        OrderCreateMessage message = new OrderCreateMessage(orderToken, userId, activityId, Instant.now());
        try {
            publishOrThrow(message);
        } catch (RuntimeException ex) {
            stockLuaExecutor.rollback(activityId, userId);
            log.warn("MQ publish failed, rolled back stock. activityId={} userId={} token={}",
                    activityId, userId, orderToken, ex);
            throw new BusinessException(ResultCode.BAD_REQUEST, "系统繁忙，库存已回滚，请重试");
        }

        return Map.of(
                "activityId", activityId,
                "orderToken", orderToken,
                "remainStock", remain
        );
    }

    private void publishOrThrow(OrderCreateMessage message) {
        CorrelationData correlation = new CorrelationData(message.orderToken());
        Boolean confirmed = rabbitTemplate.invoke(operations -> {
            operations.convertAndSend(
                    OrderMqConstants.EXCHANGE,
                    OrderMqConstants.ROUTING_KEY_CREATE,
                    message,
                    correlation
            );
            return operations.waitForConfirms(5_000);
        });
        if (!Boolean.TRUE.equals(confirmed)) {
            throw new IllegalStateException("rabbit confirm timeout or nack");
        }
    }
}
