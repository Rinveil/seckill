package com.seckill.common.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 开抢活动 ID 的 Redis bitmap 布隆过滤器。
 * 只拦截「一定不存在」的 ID，避免随机 activityId 打穿 Lua 的 4 个 key。
 * 假阳性会落到 Lua（可接受）；布隆未就绪则放行到 Lua，避免 Redis 被清空后误杀真实开抢。
 */
public class ActivityBloomFilter {

    static final int BIT_SIZE = 1 << 20;
    static final int HASH_COUNT = 4;

    private static final DefaultRedisScript<Long> MIGHT_CONTAIN = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> REBUILD = new DefaultRedisScript<>();

    static {
        MIGHT_CONTAIN.setResultType(Long.class);
        MIGHT_CONTAIN.setScriptText(
                """
                        if redis.call('GET', KEYS[2]) ~= '1' then
                          return -1
                        end
                        for i = 1, #ARGV do
                          if redis.call('GETBIT', KEYS[1], tonumber(ARGV[i])) == 0 then
                            return 0
                          end
                        end
                        return 1
                        """
        );
        REBUILD.setResultType(Long.class);
        REBUILD.setScriptText(
                """
                        local dest = KEYS[1]
                        local tmp = dest .. ':tmp'
                        redis.call('DEL', tmp)
                        if #ARGV == 0 then
                          redis.call('SET', tmp, '')
                        else
                          for i = 1, #ARGV do
                            redis.call('SETBIT', tmp, tonumber(ARGV[i]), 1)
                          end
                        end
                        redis.call('RENAME', tmp, dest)
                        redis.call('SET', KEYS[2], '1')
                        return #ARGV
                        """
        );
    }

    private final StringRedisTemplate redis;

    public ActivityBloomFilter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 用当前 OPEN 活动 ID 原子替换布隆（先写 tmp 再 RENAME，避免重建窗口误杀）。 */
    public void rebuild(Collection<Long> openIds) {
        List<String> bits = new ArrayList<>();
        if (openIds != null) {
            for (Long id : openIds) {
                if (id == null) {
                    continue;
                }
                for (long bit : bitIndexes(id)) {
                    bits.add(Long.toString(bit));
                }
            }
        }
        redis.execute(REBUILD, List.of(SeckillRedisKeys.activityBloom(), SeckillRedisKeys.activityBloomReady()),
                bits.toArray());
    }

    /**
     * @return true 该 ID 一定不在开抢集合中，可直接拒绝；false 可能存在或布隆未就绪，继续走 Lua
     */
    public boolean definitelyAbsent(long activityId) {
        Long result = redis.execute(
                MIGHT_CONTAIN,
                List.of(SeckillRedisKeys.activityBloom(), SeckillRedisKeys.activityBloomReady()),
                toArgs(bitIndexes(activityId))
        );
        return result != null && result == 0L;
    }

    static long[] bitIndexes(long id) {
        long h1 = mix(id, 0x9E3779B97F4A7C15L);
        long h2 = mix(id, 0xC2B2AE3D27D4EB4FL);
        if (h2 == 0L) {
            h2 = 1L;
        }
        long[] bits = new long[HASH_COUNT];
        for (int i = 0; i < HASH_COUNT; i++) {
            bits[i] = Long.remainderUnsigned(h1 + (long) i * h2, BIT_SIZE);
        }
        return bits;
    }

    private static long mix(long id, long seed) {
        long x = id ^ seed;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    private static Object[] toArgs(long[] bits) {
        Object[] args = new Object[bits.length];
        for (int i = 0; i < bits.length; i++) {
            args[i] = Long.toString(bits[i]);
        }
        return args;
    }
}
