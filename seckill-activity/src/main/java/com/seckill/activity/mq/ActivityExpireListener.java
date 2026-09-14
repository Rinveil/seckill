package com.seckill.activity.mq;

import com.seckill.activity.service.ActivityService;
import com.seckill.common.mq.ActivityExpireMessage;
import com.seckill.common.mq.ActivityMqConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "seckill.mq", name = "enabled", havingValue = "true")
public class ActivityExpireListener {

    private static final Logger log = LoggerFactory.getLogger(ActivityExpireListener.class);

    private final ActivityService activityService;
    private final RabbitTemplate rabbitTemplate;

    public ActivityExpireListener(ActivityService activityService, RabbitTemplate rabbitTemplate) {
        this.activityService = activityService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = ActivityMqConstants.QUEUE_EXPIRE)
    public void onMessage(ActivityExpireMessage message) {
        log.info("recv activity.expire activityId={}", message == null ? null : message.activityId());
        try {
            if (message != null) {
                activityService.expireIfOpen(message.activityId());
            }
        } catch (RuntimeException ex) {
            log.error("activity.expire failed, send DLQ. activityId={}",
                    message == null ? null : message.activityId(), ex);
            if (message != null) {
                rabbitTemplate.convertAndSend(
                        ActivityMqConstants.EXCHANGE,
                        ActivityMqConstants.ROUTING_KEY_EXPIRE_DLQ,
                        message
                );
            }
        }
    }

    @RabbitListener(queues = ActivityMqConstants.QUEUE_EXPIRE_DLQ)
    public void onDlq(ActivityExpireMessage message) {
        log.error("DLQ activity.expire activityId={}", message == null ? null : message.activityId());
    }
}
