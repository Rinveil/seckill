package com.seckill.activity.mq;

import com.seckill.activity.service.ActivityService;
import com.seckill.common.mq.ActivityExpireMessage;
import com.seckill.common.mq.ActivityMqConstants;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 活动到期关抢：失败不重试，依赖扫表兜底。
 */
@Component
@ConditionalOnProperty(prefix = "seckill.mq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = ActivityMqConstants.TOPIC_EXPIRE,
        consumerGroup = ActivityMqConstants.CG_EXPIRE
)
public class ActivityExpireListener implements RocketMQListener<ActivityExpireMessage> {

    private static final Logger log = LoggerFactory.getLogger(ActivityExpireListener.class);

    private final ActivityService activityService;

    public ActivityExpireListener(ActivityService activityService) {
        this.activityService = activityService;
    }

    @Override
    public void onMessage(ActivityExpireMessage message) {
        log.info("recv activity.expire activityId={}", message == null ? null : message.activityId());
        try {
            if (message != null) {
                activityService.expireIfOpen(message.activityId());
            }
        } catch (RuntimeException ex) {
            log.error("activity.expire failed (scan job will cover). activityId={}",
                    message == null ? null : message.activityId(), ex);
        }
    }
}
