package com.seckill.activity.controller;

import com.seckill.activity.dto.ActivityCreateRequest;
import com.seckill.activity.dto.ActivityUpdateRequest;
import com.seckill.activity.dto.ActivityView;
import com.seckill.activity.dto.RedisStockRequest;
import com.seckill.activity.service.ActivityService;
import com.seckill.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/activity")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/list")
    public Result<List<ActivityView>> list() {
        return Result.ok(activityService.list());
    }

    @GetMapping("/{id}")
    public Result<ActivityView> detail(@PathVariable long id) {
        return Result.ok(activityService.detail(id));
    }

    @PostMapping
    public Result<ActivityView> create(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody ActivityCreateRequest request
    ) {
        return Result.ok(activityService.create(role, request));
    }

    @PutMapping("/{id}")
    public Result<ActivityView> update(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable long id,
            @Valid @RequestBody ActivityUpdateRequest request
    ) {
        return Result.ok(activityService.update(role, id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable long id
    ) {
        activityService.delete(role, id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/open")
    public Result<ActivityView> open(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable long id
    ) {
        return Result.ok(activityService.open(role, id));
    }

    @PostMapping("/{id}/close")
    public Result<ActivityView> close(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable long id
    ) {
        return Result.ok(activityService.close(role, id));
    }

    @PostMapping("/{id}/preheat")
    public Result<ActivityView> preheat(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable long id
    ) {
        return Result.ok(activityService.preheat(role, id));
    }

    @PutMapping("/{id}/redis-stock")
    public Result<ActivityView> updateRedisStock(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable long id,
            @Valid @RequestBody RedisStockRequest request
    ) {
        return Result.ok(activityService.updateRedisStock(role, id, request.stock()));
    }
}
