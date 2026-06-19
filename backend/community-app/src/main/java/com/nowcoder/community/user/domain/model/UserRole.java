package com.nowcoder.community.user.domain.model;

import com.nowcoder.community.common.exception.BusinessException;

import java.util.Arrays;
import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public enum UserRole {
    USER(0, List.of("ROLE_USER")),
    ADMIN(1, List.of("ROLE_ADMIN")),
    MODERATOR(2, List.of("ROLE_MODERATOR"));

    private final int type;
    private final List<String> authorities;

    UserRole(int type, List<String> authorities) {
        this.type = type;
        this.authorities = authorities;
    }

    public int type() {
        return type;
    }

    public List<String> authorities() {
        return authorities;
    }

    public static UserRole requireValid(int type) {
        return Arrays.stream(values())
                .filter(role -> role.type == type)
                .findFirst()
                .orElseThrow(() -> new BusinessException(INVALID_ARGUMENT, "用户角色类型非法"));
    }
}
