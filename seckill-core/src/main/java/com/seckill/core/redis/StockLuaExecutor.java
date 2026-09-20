package com.seckill.core.redis;

import com.seckill.common.redis.SeckillRedisKeys;
import com.seckill.common.redis.StockRollbackHelper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Lua 预扣 / 回滚。返回值约定：
 * 预扣：>=0 剩余库存；-1 重复；-2 售罄；-3 未开抢。
 * 回滚：1 已回滚；0 无需回滚。
 */
@Component
public class StockLuaExecutor {

    private static final DefaultRedisScript<Long> DEDUCT_SCRIPT = new DefaultRedisScript<>();

    static {
        DEDUCT_SCRIPT.setResultType(Long.class);
        DEDUCT_SCRIPT.setScriptText(
                """
                        if redis.call('GET', KEYS[3]) ~= '1' then
                          return -3
                        end
                        local bought = tonumber(redis.call('GET', KEYS[2]) or '0')
                        local limit = tonumber(redis.call('GET', KEYS[4]) or '1')
                        if limit == nil or limit < 1 then limit = 1 end
                        if bought >= limit then
                          return -1
                        end
                        local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
                        if stock == nil or stock < 1 then
                          return -2
                        end
                        redis.call('DECR', KEYS[1])
                        redis.call('INCR', KEYS[2])
                        return stock - 1
                        """
        );
    }

    private final StringRedisTemplate stringRedisTemplate;

    public StockLuaExecutor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long deduct(long activityId, long userId) {
        List<String> keys = Arrays.asList(
                SeckillRedisKeys.stock(activityId),
                SeckillRedisKeys.bought(activityId, userId),
                SeckillRedisKeys.open(activityId),
                SeckillRedisKeys.limit(activityId)
        );
        Long result = stringRedisTemplate.execute(DEDUCT_SCRIPT, keys);
        return result == null ? -2L : result;
    }

    public long rollback(long activityId, long userId) {
        return StockRollbackHelper.rollback(stringRedisTemplate, activityId, userId);
    }
}
