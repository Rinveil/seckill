package com.seckill.activity.dto;

import java.time.Instant;

/** 商城公开视图：不含 redisStock 等内部数据，但含已抢数量（init - redis）。 */
public record MallView(
        long id,
        String title,
        int priceFen,
        int originPriceFen,
        int stock,
        Integer soldCount,
        String status,
        Instant startAt,
        Instant endAt,
        int limitPerUser
) {
}
