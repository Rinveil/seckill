package com.seckill.core.service;

import com.seckill.common.exception.BusinessException;
import com.seckill.common.mq.OrderCreateMessage;
import com.seckill.common.mq.OrderMqConstants;
import com.seckill.common.result.ResultCode;
import com.seckill.core.redis.StockLuaExecutor;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class SeckillService {

    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);

    private final StockLuaExecutor stockLuaExecutor;
    private final RocketMQTemplate rocketMQTemplate;

    public SeckillService(StockLuaExecutor stockLuaExecutor, RocketMQTemplate rocketMQTemplate) {
        this.stockLuaExecutor = stockLuaExecutor;
        this.rocketMQTemplate = rocketMQTemplate;
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
        SendResult result = rocketMQTemplate.syncSend(OrderMqConstants.TOPIC_CREATE, message);
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("rocketmq send failed: " + result);
        }
    }
}
