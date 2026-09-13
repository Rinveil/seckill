package com.seckill.user.dto;

import java.util.List;

public record UserPageView(
        List<UserView> list,
        long total,
        int page,
        int size
) {
}
