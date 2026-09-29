package com.seckill.activity.search;

/** ES 文档：与 mall_activity mapping 对应。 */
public record MallActivityDocument(
        long id,
        String title,
        String status,
        int priceFen,
        int originPriceFen,
        int stock,
        String startAt,
        String endAt,
        int limitPerUser
) {
}
