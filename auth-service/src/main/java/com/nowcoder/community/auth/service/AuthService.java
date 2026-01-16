package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.JwtProperties;
import com.nowcoder.community.auth.user.User;
import com.nowcoder.community.auth.user.UserMapper;
import com.nowcoder.community.common.api.AuthErrorCode;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Service
public class AuthService {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_MODERATOR = "moderator";

    private final UserMapper userMapper;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${auth.password.rehash-md5-to-bcrypt:false}")
    private boolean rehashMd5ToBcrypt;

    public AuthService(UserMapper userMapper,
                       JwtTokenService jwtTokenService,
                       RefreshTokenService refreshTokenService,
                       JwtProperties jwtProperties) {
        this.userMapper = userMapper;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.jwtProperties = jwtProperties;
    }

    public LoginResult login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }

        User user = userMapper.selectByName(username);
        if (user == null) {
            throw new BusinessException(AuthErrorCode.USERNAME_NOT_FOUND);
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(AuthErrorCode.USER_NOT_ACTIVATED);
        }

        verifyPasswordOrThrow(user, password);

        List<String> roles = resolveRoles(user.getType());
        String accessToken = jwtTokenService.issueAccessToken(user.getId(), user.getUsername(), roles);

        Duration refreshTtl = Duration.ofSeconds(jwtProperties.getRefreshTokenTtlSeconds());
        String refreshToken = refreshTokenService.issueOrReplace(user.getId(), refreshTtl);

        return new LoginResult(accessToken, jwtProperties.getAccessTokenTtlSeconds(), refreshToken, user.getId(), roles);
    }

    public LoginResult refresh(String refreshToken, String origin) {
        validateOriginForRefresh(origin);

        int userId = refreshTokenService.validateAndGetUserId(refreshToken);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        List<String> roles = resolveRoles(user.getType());
        String accessToken = jwtTokenService.issueAccessToken(user.getId(), user.getUsername(), roles);

        Duration refreshTtl = Duration.ofSeconds(jwtProperties.getRefreshTokenTtlSeconds());
        String newRefreshToken = refreshTokenService.rotate(userId, refreshToken, refreshTtl);

        return new LoginResult(accessToken, jwtProperties.getAccessTokenTtlSeconds(), newRefreshToken, user.getId(), roles);
    }

    public void logout(String refreshToken, Integer userId) {
        refreshTokenService.revoke(refreshToken, userId);
    }

    private void validateOriginForRefresh(String origin) {
        if (origin == null || origin.isBlank()) {
            return;
        }

        List<String> allowedOrigins = jwtProperties.getAllowedOrigins();
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            return;
        }
        if (!allowedOrigins.contains(origin)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "Origin 不被允许");
        }
    }

    private void verifyPasswordOrThrow(User user, String rawPassword) {
        String stored = user.getPassword();
        if (stored == null || stored.isBlank()) {
            throw new BusinessException(AuthErrorCode.PASSWORD_INVALID);
        }

        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            if (!passwordEncoder.matches(rawPassword, stored)) {
                throw new BusinessException(AuthErrorCode.PASSWORD_INVALID);
            }
            return;
        }

        // 迁移期兼容：MD5 + salt
        String salt = user.getSalt();
        if (salt == null) {
            salt = "";
        }
        String md5 = DigestUtils.md5DigestAsHex((rawPassword + salt).getBytes(StandardCharsets.UTF_8));
        if (!stored.equalsIgnoreCase(md5)) {
            throw new BusinessException(AuthErrorCode.PASSWORD_INVALID);
        }

        if (rehashMd5ToBcrypt) {
            String bcrypt = passwordEncoder.encode(rawPassword);
            userMapper.updatePassword(user.getId(), bcrypt);
        }
    }

    private List<String> resolveRoles(int type) {
        return switch (type) {
            case 1 -> List.of(ROLE_ADMIN);
            case 2 -> List.of(ROLE_MODERATOR);
            default -> List.of(ROLE_USER);
        };
    }

    public record LoginResult(String accessToken,
                              long expiresInSeconds,
                              String refreshToken,
                              int userId,
                              List<String> roles) {
    }
}

