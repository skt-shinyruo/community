package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class UserReadDomainService {

    private static final int LEVEL_SCORE_STEP = 100;

    public void assertValidUserId(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
    }

    public String normalizeUsername(String username) {
        String value = safeTrim(username);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "username 不能为空");
        }
        return value;
    }

    public String normalizeEmail(String email) {
        String value = safeTrim(email);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "email 不能为空");
        }
        return value;
    }

    public int levelForScore(int score) {
        int normalizedScore = Math.max(0, score);
        return (normalizedScore / LEVEL_SCORE_STEP) + 1;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
