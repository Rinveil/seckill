package com.seckill.core.controller;

import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.Result;
import com.seckill.core.service.SeckillService;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    @PostMapping("/{activityId}")
    public Result<Map<String, Object>> grab(
            @PathVariable long activityId,
            @RequestHeader("X-User-Id") long userId
    ) {
        return Result.ok(seckillService.grab(activityId, userId));
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> onBiz(BusinessException ex) {
        return Result.fail(ex.getCode(), ex.getMessage());
    }
}
