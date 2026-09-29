package com.seckill.activity.controller;

import com.seckill.activity.dto.MallSearchPage;
import com.seckill.activity.dto.MallView;
import com.seckill.activity.service.ActivityService;
import com.seckill.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商城公开浏览：免登录。只返回 PREHEATED/OPEN/CLOSED，不含 DRAFT 与 redisStock。
 */
@RestController
@RequestMapping("/api/mall")
public class MallController {

    private final ActivityService activityService;

    public MallController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/list")
    public Result<List<MallView>> list() {
        return Result.ok(activityService.listForMall());
    }

    @GetMapping("/search")
    public Result<MallSearchPage> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int size
    ) {
        return Result.ok(activityService.searchForMall(q, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<MallView> detail(@PathVariable long id) {
        return Result.ok(activityService.detailForMall(id));
    }
}
