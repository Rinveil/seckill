package com.seckill.order.dto;

import java.time.Instant;

public record OrderView(
        String orderNo,
        long userId,
        long activityId,
        String activityTitle,
        String status,
        int amountFen,
        Instant createdAt,
        Instant expireAt
) {
}
