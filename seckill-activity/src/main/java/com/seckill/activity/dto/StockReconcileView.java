package com.seckill.activity.dto;

/** 库存对账：initStock ≈ redisStock + created + paid。 */
public record StockReconcileView(
        long activityId,
        String status,
        int dbStock,
        Integer redisStock,
        Integer initStock,
        long createdCount,
        long paidCount,
        long cancelledCount,
        long expiredCount,
        long occupied,
        Integer expectedRedis,
        boolean consistent,
        String message
) {
}
