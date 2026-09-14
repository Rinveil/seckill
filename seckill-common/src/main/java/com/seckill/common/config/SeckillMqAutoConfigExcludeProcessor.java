package com.seckill.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MQ 关闭时排除 RabbitMQ 自动配置，避免 Broker 停用后应用仍创建连接。
 */
public class SeckillMqAutoConfigExcludeProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String RABBIT_AUTO =
            "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (resolveMqEnabled(environment)) {
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
        excludes.add(RABBIT_AUTO);
        environment.getPropertySources().addFirst(new MapPropertySource(
                "seckillMqOffExclude",
                Map.of("spring.autoconfigure.exclude", String.join(",", new ArrayList<>(excludes)))
        ));
    }

    private static boolean resolveMqEnabled(ConfigurableEnvironment environment) {
        Boolean bound = environment.getProperty("seckill.mq.enabled", Boolean.class);
        if (bound != null) {
            return bound;
        }
        for (org.springframework.core.env.PropertySource<?> ps : environment.getPropertySources()) {
            if (ps instanceof SystemEnvironmentPropertySource sys) {
                Object v = sys.getProperty("SECKILL_MQ_ENABLED");
                if (v != null) {
                    return Boolean.parseBoolean(String.valueOf(v).trim());
                }
            }
        }
        String env = environment.getProperty("SECKILL_MQ_ENABLED");
        if (env != null && !env.isBlank()) {
            return Boolean.parseBoolean(env.trim());
        }
        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
