package com.seckill.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seckill.seed-admin")
public record SeedAdminProperties(String username, String password, String nickname) {
}
