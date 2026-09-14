package com.seckill.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MQ 关闭时排除 RocketMQ 自动配置，避免 NameServer 停用后应用起不来。
 */
public class SeckillMqAutoConfigExcludeProcessor implements EnvironmentPostProcessor {

    private static final String ROCKET_MQ_AUTO =
            "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean mqEnabled = resolveMqEnabled(environment);
        if (mqEnabled) {
            return;
        }
        Set<String> excludes = new LinkedHashSet<>();
        String existing = environment.getProperty("spring.autoconfigure.exclude");
        if (existing != null && !existing.isBlank()) {
            Arrays.stream(existing.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(excludes::add);
        }
        excludes.add(ROCKET_MQ_AUTO);
        List<String> list = new ArrayList<>(excludes);
        environment.getPropertySources().addFirst(new MapPropertySource(
                "seckillMqOffExclude",
                Map.of("spring.autoconfigure.exclude", String.join(",", list))
        ));
    }

    private static boolean resolveMqEnabled(ConfigurableEnvironment environment) {
        Boolean bound = environment.getProperty("seckill.mq.enabled", Boolean.class);
        if (bound != null) {
            return bound;
        }
        String env = environment.getProperty("SECKILL_MQ_ENABLED");
        if (env != null && !env.isBlank()) {
            return Boolean.parseBoolean(env.trim());
        }
        return false;
    }
}
