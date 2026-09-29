package com.seckill.activity.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seckill.search.elasticsearch")
public record MallSearchProperties(boolean enabled, String uris) {

    public MallSearchProperties {
        if (uris == null || uris.isBlank()) {
            uris = "http://elasticsearch:9200";
        }
    }
}
