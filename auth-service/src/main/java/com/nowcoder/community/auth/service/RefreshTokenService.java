package com.nowcoder.community.auth.service;

import com.nowcoder.community.common.api.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final String TOKEN_KEY_PREFIX = "auth:refresh:token:";
    private static final String USER_KEY_PREFIX = "auth:refresh:user:";

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String issueOrReplace(int userId, Duration ttl) {
        String userKey = userKey(userId);
        String oldToken = stringRedisTemplate.opsForValue().get(userKey);
        if (oldToken != null && !oldToken.isBlank()) {
            stringRedisTemplate.delete(tokenKey(oldToken));
        }

        String newToken = generate();
        stringRedisTemplate.opsForValue().set(tokenKey(newToken), String.valueOf(userId), ttl);
        stringRedisTemplate.opsForValue().set(userKey, newToken, ttl);
        return newToken;
    }

    public int validateAndGetUserId(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_MISSING);
        }

        String userId = stringRedisTemplate.opsForValue().get(tokenKey(refreshToken));
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        String currentToken = stringRedisTemplate.opsForValue().get(userKey(Integer.parseInt(userId)));
        if (currentToken == null || !currentToken.equals(refreshToken)) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        return Integer.parseInt(userId);
    }

    public String rotate(int userId, String refreshToken, Duration ttl) {
        String currentToken = stringRedisTemplate.opsForValue().get(userKey(userId));
        if (currentToken == null || !currentToken.equals(refreshToken)) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        stringRedisTemplate.delete(tokenKey(refreshToken));

        String newToken = generate();
        stringRedisTemplate.opsForValue().set(tokenKey(newToken), String.valueOf(userId), ttl);
        stringRedisTemplate.opsForValue().set(userKey(userId), newToken, ttl);
        return newToken;
    }

    public void revoke(String refreshToken, Integer userId) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        String mappedUserId = stringRedisTemplate.opsForValue().get(tokenKey(refreshToken));
        if (mappedUserId != null && !mappedUserId.isBlank()) {
            stringRedisTemplate.delete(tokenKey(refreshToken));
            stringRedisTemplate.delete(userKey(Integer.parseInt(mappedUserId)));
            return;
        }

        if (userId != null) {
            String current = stringRedisTemplate.opsForValue().get(userKey(userId));
            if (refreshToken.equals(current)) {
                stringRedisTemplate.delete(userKey(userId));
            }
        }
    }

    private static String tokenKey(String refreshToken) {
        return TOKEN_KEY_PREFIX + refreshToken;
    }

    private static String userKey(int userId) {
        return USER_KEY_PREFIX + userId;
    }

    private static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

