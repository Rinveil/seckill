package com.seckill.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seckill.ratelimit")
public class RateLimitProperties {

    /** 桶容量（允许短时突发请求数） */
    private int capacity = 10;

    /** 每秒补充令牌数（QPS 上限/IP） */
    private int refillQps = 50;

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public int getRefillQps() { return refillQps; }
    public void setRefillQps(int refillQps) { this.refillQps = refillQps; }
}
