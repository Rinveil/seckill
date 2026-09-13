package com.seckill.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminUpdateStatusRequest(
        @NotBlank(message = "状态不能为空")
        @Pattern(regexp = "ENABLED|DISABLED", message = "状态仅支持 ENABLED 或 DISABLED")
        String status
) {
}
