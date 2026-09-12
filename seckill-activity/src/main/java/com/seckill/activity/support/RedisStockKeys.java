package com.seckill.activity.support;

/** Redis 库存 key，需与后续 core Lua 预扣保持一致。 */
public final class RedisStockKeys {

    private RedisStockKeys() {
    }

    public static String stock(long activityId) {
        return "seckill:stock:" + activityId;
    }
}
