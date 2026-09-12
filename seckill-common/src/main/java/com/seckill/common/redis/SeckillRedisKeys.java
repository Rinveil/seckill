package com.seckill.common.redis;

/** Redis key 约定：activity / core / order 共用，避免漂移。 */
public final class SeckillRedisKeys {

    private SeckillRedisKeys() {
    }

    public static String stock(long activityId) {
        return "seckill:stock:" + activityId;
    }

    /** 值为 "1" 表示活动开抢中。 */
    public static String open(long activityId) {
        return "seckill:open:" + activityId;
    }

    /** 用户已购标记（每活动限 1）。 */
    public static String bought(long activityId, long userId) {
        return "seckill:bought:" + activityId + ":" + userId;
    }
}
