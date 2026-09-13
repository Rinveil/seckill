package com.seckill.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度须为 3~32 位")
        String username,
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度须为 6~64 位")
        String password,
        @Size(max = 64, message = "昵称最长 64 位")
        String nickname,
        @NotBlank(message = "角色不能为空")
        @Pattern(regexp = "USER|ADMIN", message = "角色仅支持 USER 或 ADMIN")
        String role
) {
}
