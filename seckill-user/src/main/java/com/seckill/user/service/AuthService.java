package com.seckill.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.ResultCode;
import com.seckill.user.domain.UserAccount;
import com.seckill.user.dto.AuthResponse;
import com.seckill.user.dto.LoginRequest;
import com.seckill.user.dto.RegisterRequest;
import com.seckill.user.mapper.UserMapper;
import com.seckill.user.security.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class AuthService {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String nickname = StringUtils.hasText(request.nickname()) ? request.nickname().trim() : username;

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(ROLE_USER);
        user.setNickname(nickname);
        user.setCreatedAt(LocalDateTime.now());

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }

        String token = jwtService.issue(user.getId(), username, ROLE_USER);
        return new AuthResponse(token, user.getId(), username, ROLE_USER, nickname);
    }

    public AuthResponse login(LoginRequest request) {
        UserAccount user = userMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, request.username().trim()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        String token = jwtService.issue(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getRole(), user.getNickname());
    }

    public AuthResponse me(String authorizationHeader) {
        String token = extractBearer(authorizationHeader);
        Claims claims = jwtService.parse(token);
        Long userId = Long.valueOf(claims.getSubject());
        UserAccount user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return new AuthResponse(null, user.getId(), user.getUsername(), user.getRole(), user.getNickname());
    }

    public void ensureAdmin(String username, String rawPassword, String nickname) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username));
        if (count != null && count > 0) {
            return;
        }
        UserAccount admin = new UserAccount();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setRole(ROLE_ADMIN);
        admin.setNickname(nickname);
        admin.setCreatedAt(LocalDateTime.now());
        userMapper.insert(admin);
    }

    private static String extractBearer(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        String token = authorizationHeader.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return token;
    }
}
