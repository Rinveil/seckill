package com.seckill.activity.support;

import com.seckill.common.redis.SeckillRedisKeys;

/** @deprecated 使用 {@link SeckillRedisKeys}；保留转发以免遗漏引用。 */
public final class RedisStockKeys {

    private RedisStockKeys() {
    }

    public static String stock(long activityId) {
        return SeckillRedisKeys.stock(activityId);
    }

    public static String open(long activityId) {
        return SeckillRedisKeys.open(activityId);
    }
}
