package com.seckill.activity.config;

import com.seckill.common.config.SeckillFeatureProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SeckillFeatureProperties.class)
public class ActivityFeatureConfig {
}
