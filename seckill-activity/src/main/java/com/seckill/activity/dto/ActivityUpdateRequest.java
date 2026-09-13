package com.seckill.activity.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ActivityUpdateRequest(
        @NotBlank(message = "活动标题不能为空")
        @Size(max = 128, message = "活动标题最长 128 字")
        String title,
        @NotNull(message = "秒杀价不能为空") @Min(value = 1, message = "秒杀价须大于 0（单位：分）") Integer priceFen,
        @NotNull(message = "原价不能为空") @Min(value = 1, message = "原价须大于 0（单位：分）") Integer originPriceFen,
        @NotNull(message = "配置库存不能为空") @Min(value = 0, message = "配置库存不能为负数") Integer stock,
        @NotNull(message = "开始时间不能为空") Instant startAt,
        @NotNull(message = "结束时间不能为空") Instant endAt
) {
}
