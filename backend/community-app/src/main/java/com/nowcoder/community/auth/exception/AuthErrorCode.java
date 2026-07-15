package com.nowcoder.community.auth.exception;

import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.exception.ErrorKind;

/**
 * auth 域错误码（登录/注册/验证码/刷新等）。
 *
 * <p>约定：ErrorKind 表达稳定类别，Web adapter 映射 HTTP status。</p>
 */
public enum AuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(10001, "用户名或密码错误", ErrorKind.UNAUTHENTICATED),
    USER_DISABLED(10002, "账号未激活或被禁用", ErrorKind.FORBIDDEN),

    TOKEN_INVALID(10003, "令牌无效或已过期", ErrorKind.UNAUTHENTICATED),
    REFRESH_TOKEN_INVALID(10004, "刷新令牌无效或已过期", ErrorKind.UNAUTHENTICATED),

    CAPTCHA_REQUIRED(10005, "需要验证码", ErrorKind.INVALID_INPUT),
    CAPTCHA_INVALID(10006, "验证码不正确或已失效", ErrorKind.INVALID_INPUT),

    PASSWORD_RESET_INVALID(10007, "重置凭证无效或已过期", ErrorKind.INVALID_INPUT),

    CAPTCHA_GENERATE_FAILED(10008, "验证码生成失败", ErrorKind.INTERNAL),

    REGISTRATION_CODE_INVALID(10009, "注册验证码不正确", ErrorKind.INVALID_INPUT),
    REGISTRATION_CODE_EXPIRED(10010, "注册验证码已过期", ErrorKind.INVALID_INPUT),
    REGISTRATION_CODE_RESEND_COOLDOWN(10011, "注册验证码发送过于频繁", ErrorKind.THROTTLED),
    REGISTRATION_CODE_TOO_MANY_ATTEMPTS(10012, "注册验证码错误次数过多，请重新获取", ErrorKind.INVALID_INPUT),
    REGISTRATION_CONTEXT_INVALID(10013, "注册上下文已失效，请重新注册", ErrorKind.NOT_FOUND),
    REGISTRATION_ACTIVATED_LOGIN_REQUIRED(10014, "注册已完成，请直接登录", ErrorKind.CONFLICT);

    private final int code;
    private final String message;
    private final ErrorKind kind;

    AuthErrorCode(int code, String message, ErrorKind kind) {
        this.code = code;
        this.message = message;
        this.kind = kind;
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
    public ErrorKind getKind() {
        return kind;
    }
}
