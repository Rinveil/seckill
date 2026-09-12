package com.seckill.user.dto;

public record AuthResponse(
        String token,
        Long userId,
        String username,
        String role,
        String nickname
) {
}
