package com.seckill.order.config;

import com.seckill.common.config.SeckillFeatureProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OrderProperties.class, SeckillFeatureProperties.class})
public class OrderFeatureConfig {
}
