package com.seckill.common.config;

import com.seckill.common.redis.ActivityBloomFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class ActivityBloomFilterConfig {

    @Bean
    public ActivityBloomFilter activityBloomFilter(StringRedisTemplate stringRedisTemplate) {
        return new ActivityBloomFilter(stringRedisTemplate);
    }
}
