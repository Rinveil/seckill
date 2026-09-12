package com.seckill.activity.dto;

import java.time.Instant;

public record ActivityView(
        long id,
        String title,
        String cover,
        int priceFen,
        int originPriceFen,
        int stock,
        Instant startAt,
        Instant endAt
) {
}
