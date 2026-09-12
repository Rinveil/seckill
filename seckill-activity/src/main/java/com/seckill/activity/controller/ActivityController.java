package com.seckill.activity.controller;

import com.seckill.activity.dto.ActivityView;
import com.seckill.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/activity")
public class ActivityController {

    private final ActivityView demo = new ActivityView(
            1L,
            "爆款机械键盘秒杀",
            "",
            9900,
            39900,
            100,
            Instant.now().plus(30, ChronoUnit.SECONDS),
            Instant.now().plus(1, ChronoUnit.HOURS)
    );

    @GetMapping("/list")
    public Result<List<ActivityView>> list() {
        return Result.ok(List.of(demo));
    }

    @GetMapping("/{id}")
    public Result<ActivityView> detail(@PathVariable long id) {
        return Result.ok(demo);
    }
}
