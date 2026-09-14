package com.seckill.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 功能开关：MQ 关闭时抢购同步落单；定时任务关闭时不注册扫表 Job。
 */
@ConfigurationProperties(prefix = "seckill")
public record SeckillFeatureProperties(Mq mq, Schedule schedule) {

    public SeckillFeatureProperties {
        if (mq == null) {
            mq = new Mq(false);
        }
        if (schedule == null) {
            schedule = new Schedule(false);
        }
    }

    public record Mq(boolean enabled) {
    }

    public record Schedule(boolean enabled) {
    }

    public boolean mqEnabled() {
        return mq != null && mq.enabled();
    }

    public boolean scheduleEnabled() {
        return schedule != null && schedule.enabled();
    }
}
