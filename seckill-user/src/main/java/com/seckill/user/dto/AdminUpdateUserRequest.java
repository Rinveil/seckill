package com.seckill.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminUpdateUserRequest(
        @Size(max = 64, message = "昵称最长 64 位")
        String nickname,
        @NotBlank(message = "角色不能为空")
        @Pattern(regexp = "USER|ADMIN", message = "角色仅支持 USER 或 ADMIN")
        String role
) {
}
