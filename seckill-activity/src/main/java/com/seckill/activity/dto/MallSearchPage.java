package com.seckill.activity.dto;

import java.util.List;

public record MallSearchPage(
        List<MallSearchHit> items,
        long total,
        int page,
        int size,
        boolean fromElasticsearch
) {
}
