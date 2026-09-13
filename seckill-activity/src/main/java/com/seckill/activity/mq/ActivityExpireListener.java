package com.seckill.activity.mq;

import com.seckill.common.mq.ActivityExpireMessage;
import com.seckill.common.mq.ActivityMqConstants;
import com.seckill.activity.service.ActivityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ActivityExpireListener {

    private static final Logger log = LoggerFactory.getLogger(ActivityExpireListener.class);

    private final ActivityService activityService;

    public ActivityExpireListener(ActivityService activityService) {
        this.activityService = activityService;
    }

    @RabbitListener(queues = ActivityMqConstants.QUEUE_EXPIRE)
    public void onMessage(ActivityExpireMessage message) {
        log.info("recv activity.expire activityId={}", message == null ? null : message.activityId());
        if (message != null) {
            activityService.expireIfOpen(message.activityId());
        }
    }
}
