package com.seckill.activity.config;

import com.seckill.common.config.ActivityBloomFilterConfig;
import com.seckill.common.config.SeckillFeatureProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(ActivityBloomFilterConfig.class)
@EnableConfigurationProperties(SeckillFeatureProperties.class)
public class ActivityFeatureConfig {
}
