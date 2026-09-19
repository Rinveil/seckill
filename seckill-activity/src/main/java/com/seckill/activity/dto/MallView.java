package com.seckill.activity.dto;

import java.time.Instant;

/** 商城公开视图：不含 redisStock 等内部数据。 */
public record MallView(
        long id,
        String title,
        int priceFen,
        int originPriceFen,
        int stock,
        String status,
        Instant startAt,
        Instant endAt
) {
}
