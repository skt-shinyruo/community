package com.nowcoder.community.common.api;

public enum AuthErrorCode implements ErrorCode {

    LOGIN_FAILED(10001, "用户名或密码错误"),
    USER_DISABLED(10002, "账号未激活或已禁用"),
    TOKEN_INVALID(10003, "令牌无效或已过期"),
    REFRESH_TOKEN_INVALID(10004, "刷新令牌无效或已过期"),

    CAPTCHA_REQUIRED(10005, "需要验证码"),
    CAPTCHA_INVALID(10006, "验证码不正确或已失效"),

    PASSWORD_RESET_INVALID(10007, "重置凭证无效或已过期");

    private final int code;
    private final String message;

    AuthErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
