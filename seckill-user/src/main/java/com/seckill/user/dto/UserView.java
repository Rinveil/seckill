package com.seckill.user.dto;

import java.time.Instant;

public record UserView(
        long id,
        String username,
        String role,
        String nickname,
        String status,
        Instant createdAt
) {
}
