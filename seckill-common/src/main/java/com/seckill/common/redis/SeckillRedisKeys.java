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

    /** 用户已购计数（每活动按 limitPerUser 限购）。 */
    public static String bought(long activityId, long userId) {
        return "seckill:bought:" + activityId + ":" + userId;
    }

    /** 预热/改 Redis 时的基准库存，供对账：init ≈ redis + CREATED + PAID。 */
    public static String stockInit(long activityId) {
        return "seckill:stock:init:" + activityId;
    }

    /** 每用户限购数量，预热时写入。 */
    public static String limit(long activityId) {
        return "seckill:limit:" + activityId;
    }
}
