package com.seckill.common.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Arrays;
import java.util.List;

/**
 * 与 core 预扣对称的回滚：已购 -1 + 库存 +1。
 * 返回 1=已回滚，0=无需回滚。
 */
public final class StockRollbackHelper {

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT = new DefaultRedisScript<>();

    static {
        ROLLBACK_SCRIPT.setResultType(Long.class);
        ROLLBACK_SCRIPT.setScriptText(
                """
                        local bought = tonumber(redis.call('GET', KEYS[2]) or '0')
                        if bought > 0 then
                          redis.call('DECR', KEYS[2])
                          redis.call('INCR', KEYS[1])
                          return 1
                        end
                        return 0
                        """
        );
    }

    private StockRollbackHelper() {
    }

    public static long rollback(StringRedisTemplate redis, long activityId, long userId) {
        List<String> keys = Arrays.asList(
                SeckillRedisKeys.stock(activityId),
                SeckillRedisKeys.bought(activityId, userId)
        );
        Long result = redis.execute(ROLLBACK_SCRIPT, keys);
        return result == null ? 0L : result;
    }
}
