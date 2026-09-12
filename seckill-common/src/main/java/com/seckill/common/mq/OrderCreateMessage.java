package com.seckill.common.mq;

import java.time.Instant;

/** 预扣成功后投递；order 幂等建单。 */
public record OrderCreateMessage(
        String orderToken,
        long userId,
        long activityId,
        Instant createdAt
) {
}
