package com.seckill.user.controller;

import com.seckill.common.result.Result;
import com.seckill.user.dto.LoginRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
public class AuthController {

    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody LoginRequest request) {
        return Result.ok(Map.of(
                "token", UUID.randomUUID().toString().replace("-", ""),
                "mobile", request.mobile() == null ? "13800000000" : request.mobile()
        ));
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(Map.of("userId", 10001, "nickname", "秒杀用户"));
    }
}
