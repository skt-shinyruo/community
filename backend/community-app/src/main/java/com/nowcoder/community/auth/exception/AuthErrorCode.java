package com.nowcoder.community.auth.exception;

import com.nowcoder.community.common.exception.ErrorCode;

/**
 * auth 域错误码（登录/注册/验证码/刷新等）。
 *
 * <p>约定：HTTP status 表达“错误类别”，Result.code 表达“业务细分”。</p>
 */
public enum AuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(10001, "用户名或密码错误", 401),
    USER_DISABLED(10002, "账号未激活或被禁用", 403),

    TOKEN_INVALID(10003, "令牌无效或已过期", 401),
    REFRESH_TOKEN_INVALID(10004, "刷新令牌无效或已过期", 401),

    CAPTCHA_REQUIRED(10005, "需要验证码", 400),
    CAPTCHA_INVALID(10006, "验证码不正确或已失效", 400),

    PASSWORD_RESET_INVALID(10007, "重置凭证无效或已过期", 400),

    CAPTCHA_GENERATE_FAILED(10008, "验证码生成失败", 500),

    REGISTRATION_CODE_INVALID(10009, "注册验证码不正确", 400),
    REGISTRATION_CODE_EXPIRED(10010, "注册验证码已过期", 400),
    REGISTRATION_CODE_RESEND_COOLDOWN(10011, "注册验证码发送过于频繁", 429),
    REGISTRATION_CODE_TOO_MANY_ATTEMPTS(10012, "注册验证码错误次数过多，请重新获取", 400);

    private final int code;
    private final String message;
    private final int httpStatus;

    AuthErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
