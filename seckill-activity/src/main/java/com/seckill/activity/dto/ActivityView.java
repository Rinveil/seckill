package com.seckill.activity.dto;

import java.time.Instant;

public record ActivityView(
        long id,
        String title,
        int priceFen,
        int originPriceFen,
        int stock,
        Integer redisStock,
        String status,
        Instant startAt,
        Instant endAt,
        int limitPerUser
) {
}
