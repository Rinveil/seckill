package com.seckill.activity.dto;

public record MallSearchHit(
        MallView item,
        String highlightedTitle
) {
}
