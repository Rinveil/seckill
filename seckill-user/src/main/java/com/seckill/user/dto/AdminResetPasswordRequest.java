package com.seckill.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminResetPasswordRequest(
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度须为 6~64 位")
        String password
) {
}
