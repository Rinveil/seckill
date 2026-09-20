package com.seckill.core.config;

import com.seckill.common.config.ActivityBloomFilterConfig;
import com.seckill.common.config.SeckillFeatureProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestTemplate;

@Configuration
@Import(ActivityBloomFilterConfig.class)
@EnableConfigurationProperties(SeckillFeatureProperties.class)
public class CoreFeatureConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
