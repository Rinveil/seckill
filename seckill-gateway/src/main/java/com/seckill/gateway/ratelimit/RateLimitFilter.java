package com.seckill.gateway.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.common.result.ResultCode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易令牌桶限流（单机内存）：
 * - /api/seckill/** 与登录/注册分桶
 * - 默认 50 QPS/IP，桶容量 10
 * - 客户端 IP 取 nginx 的 X-Real-IP；超限 HTTP 429、body code=1002
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper;
    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(ObjectMapper objectMapper, RateLimitProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!shouldLimit(path)) {
            return chain.filter(exchange);
        }
        String clientIp = extractClientIp(exchange.getRequest());
        String bucketKey = path.startsWith("/api/seckill/") ? "seckill:" + clientIp : "auth:" + clientIp;
        TokenBucket bucket = buckets.computeIfAbsent(bucketKey, k ->
                new TokenBucket(properties.getCapacity(), properties.getRefillQps()));
        if (!bucket.tryConsume()) {
            return tooManyRequests(exchange, clientIp);
        }
        return chain.filter(exchange);
    }

    private static boolean shouldLimit(String path) {
        return path.startsWith("/api/seckill/")
                || "/api/user/login".equals(path)
                || "/api/user/register".equals(path);
    }

    /**
     * 信任 nginx 写入的 X-Real-IP（$remote_addr），不取客户端伪造的 XFF 首段。
     */
    private String extractClientIp(ServerHttpRequest request) {
        String real = request.getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(real)) {
            return real.trim();
        }
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            String[] parts = xff.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, String ip) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ResultCode.RATE_LIMITED.code());
        body.put("message", "请求过于频繁，请稍后重试");
        body.put("data", null);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = "{\"code\":1002,\"message\":\"请求过于频繁\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /** 早于 JWT 校验（-100），先挡洪峰再验签。 */
    @Override
    public int getOrder() {
        return -110;
    }

    static class TokenBucket {
        private final int capacity;
        private final double refillPerMs;
        private double tokens;
        private long lastRefillNanos;

        TokenBucket(int capacity, int refillQps) {
            this.capacity = capacity;
            this.refillPerMs = refillQps / 1000.0;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            long now = System.nanoTime();
            long elapsedMs = (now - lastRefillNanos) / 1_000_000;
            tokens = Math.min(capacity, tokens + elapsedMs * refillPerMs);
            lastRefillNanos = now;
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }
    }
}
