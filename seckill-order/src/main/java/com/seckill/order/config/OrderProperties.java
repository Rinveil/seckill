package com.seckill.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seckill.order")
public record OrderProperties(int expireMinutes) {

    public OrderProperties {
        if (expireMinutes <= 0) {
            expireMinutes = 3;
        }
    }
}
