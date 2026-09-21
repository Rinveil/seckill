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

    /** 开抢中活动 ID 的布隆过滤器（Redis bitmap）。 */
    public static String activityBloom() {
        return "seckill:bloom:activity";
    }

    /** 布隆已构建标记；缺失时 core 对 Lua 失败开放，避免误杀。 */
    public static String activityBloomReady() {
        return "seckill:bloom:ready";
    }

    /** 某活动下全部已购计数，删除活动时 SCAN/KEYS 清理。 */
    public static String boughtPattern(long activityId) {
        return "seckill:bought:" + activityId + ":*";
    }

    /** 禁用账号标记；网关验签后若存在则拒绝，即使 JWT 未过期。 */
    public static String userDisabled(long userId) {
        return "seckill:user:disabled:" + userId;
    }

    public static String userDisabledPattern() {
        return "seckill:user:disabled:*";
    }
}
