package com.seckill.gateway.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.common.redis.SeckillRedisKeys;
import com.seckill.common.result.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@EnableConfigurationProperties(SeckillGatewayProperties.class)
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_USERNAME = "X-Username";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final SeckillGatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final ReactiveStringRedisTemplate redis;
    private final SecretKey key;

    public JwtAuthGlobalFilter(
            SeckillGatewayProperties properties,
            ObjectMapper objectMapper,
            ReactiveStringRedisTemplate redis
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.redis = redis;
        byte[] bytes = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (HttpMethod.OPTIONS.equals(request.getMethod()) || isWhitelisted(path)) {
            ServerHttpRequest cleaned = stripUserHeaders(request).build();
            return chain.filter(exchange.mutate().request(cleaned).build());
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        try {
            String token = authorization.substring(7).trim();
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String role = stringClaim(claims, "role");
            String username = stringClaim(claims, "username");
            if (!StringUtils.hasText(userId)) {
                return unauthorized(exchange);
            }
            long uid;
            try {
                uid = Long.parseLong(userId);
            } catch (NumberFormatException ex) {
                return unauthorized(exchange);
            }

            ServerHttpRequest mutated = stripUserHeaders(request)
                    .header(HEADER_USER_ID, userId)
                    .header(HEADER_USER_ROLE, role == null ? "" : role)
                    .header(HEADER_USERNAME, username == null ? "" : username)
                    .build();
            ServerWebExchange next = exchange.mutate().request(mutated).build();
            return redis.hasKey(SeckillRedisKeys.userDisabled(uid))
                    .onErrorReturn(false)
                    .defaultIfEmpty(false)
                    .flatMap(disabled -> Boolean.TRUE.equals(disabled)
                            ? accountDisabled(exchange)
                            : chain.filter(next));
        } catch (Exception ex) {
            return unauthorized(exchange);
        }
    }

    private boolean isWhitelisted(String path) {
        return properties.getAuth().getWhitelist().stream()
                .filter(StringUtils.hasText)
                .anyMatch(w -> w.contains("*")
                        ? PATH_MATCHER.match(w, path)
                        : path.equals(w) || path.startsWith(w + "/"));
    }

    private static ServerHttpRequest.Builder stripUserHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USER_ROLE);
                    headers.remove(HEADER_USERNAME);
                });
    }

    private static String stringClaim(Claims claims, String name) {
        Object value = claims.get(name);
        return value == null ? null : String.valueOf(value);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        return json(exchange, HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED.code(), "未登录");
    }

    private Mono<Void> accountDisabled(ServerWebExchange exchange) {
        return json(
                exchange,
                HttpStatus.FORBIDDEN,
                ResultCode.ACCOUNT_DISABLED.code(),
                ResultCode.ACCOUNT_DISABLED.message()
        );
    }

    private Mono<Void> json(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
