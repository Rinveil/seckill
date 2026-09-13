package com.seckill.user.controller;

import com.seckill.common.result.Result;
import com.seckill.user.dto.AdminCreateUserRequest;
import com.seckill.user.dto.AdminResetPasswordRequest;
import com.seckill.user.dto.AdminUpdateStatusRequest;
import com.seckill.user.dto.AdminUpdateUserRequest;
import com.seckill.user.dto.UserPageView;
import com.seckill.user.dto.UserView;
import com.seckill.user.service.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/admin")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping("/list")
    public Result<UserPageView> list(
            @RequestHeader(value = "X-User-Role", required = false) String operatorRole,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return Result.ok(userAdminService.list(operatorRole, keyword, role, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<UserView> detail(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable long id
    ) {
        return Result.ok(userAdminService.detail(role, id));
    }

    @PostMapping
    public Result<UserView> create(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody AdminCreateUserRequest request
    ) {
        return Result.ok(userAdminService.create(role, request));
    }

    @PutMapping("/{id}")
    public Result<UserView> update(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader("X-User-Id") long operatorId,
            @PathVariable long id,
            @Valid @RequestBody AdminUpdateUserRequest request
    ) {
        return Result.ok(userAdminService.update(role, operatorId, id, request));
    }

    @PutMapping("/{id}/status")
    public Result<UserView> updateStatus(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader("X-User-Id") long operatorId,
            @PathVariable long id,
            @Valid @RequestBody AdminUpdateStatusRequest request
    ) {
        return Result.ok(userAdminService.updateStatus(role, operatorId, id, request));
    }

    @PutMapping("/{id}/password")
    public Result<UserView> resetPassword(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable long id,
            @Valid @RequestBody AdminResetPasswordRequest request
    ) {
        return Result.ok(userAdminService.resetPassword(role, id, request));
    }
}
