package com.seckill.activity.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RedisStockRequest(
        @NotNull(message = "Redis 库存不能为空")
        @Min(value = 0, message = "Redis 库存不能为负数")
        Integer stock
) {
}
