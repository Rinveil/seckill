package com.seckill.core.mq;

import java.time.Instant;

/** 预扣成功后投递；order 服务幂等建单（第 7 步消费）。 */
public record OrderCreateMessage(
        String orderToken,
        long userId,
        long activityId,
        Instant createdAt
) {
}
