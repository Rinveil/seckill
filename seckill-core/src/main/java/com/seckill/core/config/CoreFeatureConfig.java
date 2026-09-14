package com.seckill.core.config;

import com.seckill.common.config.SeckillFeatureProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(SeckillFeatureProperties.class)
public class CoreFeatureConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
