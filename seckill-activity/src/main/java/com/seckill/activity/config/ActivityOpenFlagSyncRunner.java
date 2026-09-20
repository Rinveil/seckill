package com.seckill.activity.config;

import com.seckill.common.redis.ActivityBloomFilter;
import com.seckill.common.redis.SeckillRedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** 将 DB 中已开抢活动同步到 Redis open 标记与布隆过滤器，供 core 校验。 */
@Component
@Order(2)
public class ActivityOpenFlagSyncRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ActivityOpenFlagSyncRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ActivityBloomFilter activityBloomFilter;

    public ActivityOpenFlagSyncRunner(
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate stringRedisTemplate,
            ActivityBloomFilter activityBloomFilter
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.activityBloomFilter = activityBloomFilter;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Long> openIds = jdbcTemplate.query(
                "SELECT id FROM t_activity WHERE status = 1",
                (rs, rowNum) -> rs.getLong("id")
        );
        for (Long id : openIds) {
            stringRedisTemplate.opsForValue().set(SeckillRedisKeys.open(id), "1");
        }
        try {
            activityBloomFilter.rebuild(openIds);
        } catch (RuntimeException ex) {
            log.warn("rebuild activity bloom failed, core will fail-open to Lua", ex);
        }
        log.info("synced {} open activity flag(s) and bloom to Redis", openIds.size());
    }
}
