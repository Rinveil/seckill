package com.seckill.activity.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RedisStockRequest(
        @NotNull @Min(0) Integer stock
) {
}
