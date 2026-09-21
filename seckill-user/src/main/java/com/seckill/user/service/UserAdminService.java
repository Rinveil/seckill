package com.seckill.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.ResultCode;
import com.seckill.user.domain.UserAccount;
import com.seckill.user.dto.AdminCreateUserRequest;
import com.seckill.user.dto.AdminResetPasswordRequest;
import com.seckill.user.dto.AdminUpdateStatusRequest;
import com.seckill.user.dto.AdminUpdateUserRequest;
import com.seckill.user.dto.UserPageView;
import com.seckill.user.dto.UserView;
import com.seckill.user.mapper.UserMapper;
import com.seckill.user.support.UserDisabledStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class UserAdminService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_PAGE_SIZE = 50;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserDisabledStore userDisabledStore;

    public UserAdminService(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            UserDisabledStore userDisabledStore
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.userDisabledStore = userDisabledStore;
    }

    public UserPageView list(String roleHeader, String keyword, String role, String status, int page, int size) {
        requireAdmin(roleHeader);
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<UserAccount> qw = new LambdaQueryWrapper<UserAccount>()
                .orderByDesc(UserAccount::getId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(UserAccount::getUsername, kw).or().like(UserAccount::getNickname, kw));
        }
        if (StringUtils.hasText(role)) {
            qw.eq(UserAccount::getRole, role.trim().toUpperCase());
        }
        if (StringUtils.hasText(status)) {
            qw.eq(UserAccount::getStatus, toStatusCode(status.trim().toUpperCase()));
        }

        long total = userMapper.selectCount(qw);
        long offset = (long) (p - 1) * s;
        qw.last("LIMIT " + s + " OFFSET " + offset);
        List<UserView> list = userMapper.selectList(qw).stream().map(this::toView).toList();
        return new UserPageView(list, total, p, s);
    }

    public UserView detail(String roleHeader, long id) {
        requireAdmin(roleHeader);
        return toView(requireUser(id));
    }

    public UserView create(String roleHeader, AdminCreateUserRequest request) {
        requireAdmin(roleHeader);
        String username = request.username().trim();
        String nickname = StringUtils.hasText(request.nickname()) ? request.nickname().trim() : username;
        String role = request.role().trim().toUpperCase();

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setNickname(nickname);
        user.setStatus(UserAccount.STATUS_ENABLED);
        user.setCreatedAt(LocalDateTime.now(ZONE));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        return toView(user);
    }

    public UserView update(String roleHeader, long operatorId, long id, AdminUpdateUserRequest request) {
        requireAdmin(roleHeader);
        UserAccount user = requireUser(id);
        String newRole = request.role().trim().toUpperCase();
        String nickname = StringUtils.hasText(request.nickname())
                ? request.nickname().trim()
                : user.getNickname();

        if (operatorId == id && !AuthService.ROLE_ADMIN.equals(newRole)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能将自己降为普通用户");
        }
        if (isEnabledAdmin(user) && !AuthService.ROLE_ADMIN.equals(newRole)) {
            assertNotLastEnabledAdmin(id);
        }

        user.setNickname(nickname);
        user.setRole(newRole);
        userMapper.updateById(user);
        return toView(requireUser(id));
    }

    public UserView updateStatus(String roleHeader, long operatorId, long id, AdminUpdateStatusRequest request) {
        requireAdmin(roleHeader);
        UserAccount user = requireUser(id);
        int newStatus = toStatusCode(request.status().trim().toUpperCase());

        if (operatorId == id && newStatus == UserAccount.STATUS_DISABLED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能禁用自己的账号");
        }
        if (isEnabledAdmin(user) && newStatus == UserAccount.STATUS_DISABLED) {
            assertNotLastEnabledAdmin(id);
        }

        user.setStatus(newStatus);
        userMapper.updateById(user);
        userDisabledStore.setDisabled(id, newStatus == UserAccount.STATUS_DISABLED);
        return toView(requireUser(id));
    }

    public UserView resetPassword(String roleHeader, long id, AdminResetPasswordRequest request) {
        requireAdmin(roleHeader);
        UserAccount user = requireUser(id);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userMapper.updateById(user);
        return toView(user);
    }

    private void assertNotLastEnabledAdmin(long excludeId) {
        long others = userMapper.selectCount(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getRole, AuthService.ROLE_ADMIN)
                        .eq(UserAccount::getStatus, UserAccount.STATUS_ENABLED)
                        .ne(UserAccount::getId, excludeId)
        );
        if (others <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "至少保留一名启用中的管理员");
        }
    }

    private static boolean isEnabledAdmin(UserAccount user) {
        return AuthService.ROLE_ADMIN.equals(user.getRole())
                && user.getStatus() != null
                && user.getStatus() == UserAccount.STATUS_ENABLED;
    }

    private UserAccount requireUser(long id) {
        UserAccount user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户不存在");
        }
        return user;
    }

    private void requireAdmin(String role) {
        if (!AuthService.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "需要管理员权限");
        }
    }

    private static int toStatusCode(String status) {
        if ("DISABLED".equals(status)) {
            return UserAccount.STATUS_DISABLED;
        }
        if ("ENABLED".equals(status)) {
            return UserAccount.STATUS_ENABLED;
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "状态仅支持 ENABLED 或 DISABLED");
    }

    private UserView toView(UserAccount user) {
        boolean enabled = user.getStatus() == null || user.getStatus() == UserAccount.STATUS_ENABLED;
        return new UserView(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getNickname(),
                enabled ? "ENABLED" : "DISABLED",
                user.getCreatedAt() == null ? null : user.getCreatedAt().atZone(ZONE).toInstant()
        );
    }
}
