package com.seckill.activity.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ActivityCreateRequest(
        @NotBlank String title,
        @NotNull @Min(1) Integer priceFen,
        @NotNull @Min(1) Integer originPriceFen,
        @NotNull @Min(0) Integer stock,
        @NotNull Instant startAt,
        @NotNull Instant endAt
) {
}
