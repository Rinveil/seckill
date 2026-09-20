package com.seckill.core.service;

import com.seckill.common.config.SeckillFeatureProperties;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.mq.OrderCreateMessage;
import com.seckill.common.mq.OrderMqConstants;
import com.seckill.common.redis.ActivityBloomFilter;
import com.seckill.common.result.ResultCode;
import com.seckill.core.client.OrderCreateClient;
import com.seckill.core.redis.StockLuaExecutor;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class SeckillService {

    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);

    private final StockLuaExecutor stockLuaExecutor;
    private final SeckillFeatureProperties featureProperties;
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplate;
    private final OrderCreateClient orderCreateClient;
    private final ActivityBloomFilter activityBloomFilter;

    public SeckillService(
            StockLuaExecutor stockLuaExecutor,
            SeckillFeatureProperties featureProperties,
            ObjectProvider<RocketMQTemplate> rocketMQTemplate,
            OrderCreateClient orderCreateClient,
            ActivityBloomFilter activityBloomFilter
    ) {
        this.stockLuaExecutor = stockLuaExecutor;
        this.featureProperties = featureProperties;
        this.rocketMQTemplate = rocketMQTemplate;
        this.orderCreateClient = orderCreateClient;
        this.activityBloomFilter = activityBloomFilter;
    }

    public Map<String, Object> grab(long activityId, long userId) {
        if (definitelyAbsent(activityId)) {
            throw new BusinessException(ResultCode.NOT_STARTED);
        }
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
            dispatchCreate(message);
        } catch (RuntimeException ex) {
            stockLuaExecutor.rollback(activityId, userId);
            log.warn("order create failed, rolled back stock. activityId={} userId={} token={} mq={}",
                    activityId, userId, orderToken, featureProperties.mqEnabled(), ex);
            throw new BusinessException(ResultCode.BAD_REQUEST, "系统繁忙，库存已回滚，请重试");
        }

        return Map.of(
                "activityId", activityId,
                "orderToken", orderToken,
                "remainStock", remain
        );
    }

    private boolean definitelyAbsent(long activityId) {
        try {
            return activityBloomFilter.definitelyAbsent(activityId);
        } catch (RuntimeException ex) {
            log.warn("activity bloom check failed, fail-open to Lua. activityId={}", activityId, ex);
            return false;
        }
    }

    private void dispatchCreate(OrderCreateMessage message) {
        if (!featureProperties.mqEnabled()) {
            orderCreateClient.createSync(message);
            return;
        }
        RocketMQTemplate template = rocketMQTemplate.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("RocketMQTemplate missing while seckill.mq.enabled=true");
        }
        SendResult result = template.syncSend(OrderMqConstants.TOPIC_CREATE, message);
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("rocketmq send failed: " + result);
        }
    }
}
