package com.seckill.user.support;

import com.seckill.common.redis.SeckillRedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** 禁用账号 Redis 标记，供网关在 JWT 未过期时拒绝访问。 */
@Component
public class UserDisabledStore {

    private static final Logger log = LoggerFactory.getLogger(UserDisabledStore.class);

    private final StringRedisTemplate redis;

    public UserDisabledStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void setDisabled(long userId, boolean disabled) {
        try {
            String key = SeckillRedisKeys.userDisabled(userId);
            if (disabled) {
                redis.opsForValue().set(key, "1");
            } else {
                redis.delete(key);
            }
        } catch (RuntimeException ex) {
            log.warn("write disabled flag failed, userId={} disabled={}", userId, disabled, ex);
        }
    }

    public void replaceAll(List<Long> disabledUserIds) {
        Set<String> existing = redis.keys(SeckillRedisKeys.userDisabledPattern());
        if (existing != null && !existing.isEmpty()) {
            redis.delete(existing);
        }
        if (disabledUserIds == null) {
            return;
        }
        for (Long id : disabledUserIds) {
            if (id != null) {
                redis.opsForValue().set(SeckillRedisKeys.userDisabled(id), "1");
            }
        }
        log.info("synced {} disabled user flag(s) to Redis", disabledUserIds.size());
    }
}
