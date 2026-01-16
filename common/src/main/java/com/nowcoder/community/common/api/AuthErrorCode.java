package com.nowcoder.community.common.api;

public enum AuthErrorCode implements ErrorCode {
    USERNAME_NOT_FOUND(40101, "账号不存在"),
    USER_NOT_ACTIVATED(40102, "账号未激活"),
    PASSWORD_INVALID(40103, "密码错误"),

    REFRESH_TOKEN_MISSING(40110, "缺少刷新令牌"),
    REFRESH_TOKEN_INVALID(40111, "刷新令牌无效"),
    REFRESH_TOKEN_EXPIRED(40112, "刷新令牌已过期");

    private final int code;
    private final String message;

    AuthErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}

