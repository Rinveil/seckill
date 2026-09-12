package com.seckill.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seckill.jwt")
public record JwtProperties(String secret, int expireHours) {
}
