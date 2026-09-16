package com.seckill.order.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.seckill.common.config.SeckillFeatureProperties;

@Configuration
@EnableConfigurationProperties({OrderProperties.class, SeckillFeatureProperties.class})
public class OrderMqConfig {
}
