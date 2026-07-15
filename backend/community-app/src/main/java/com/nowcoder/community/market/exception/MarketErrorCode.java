package com.nowcoder.community.market.exception;

import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.exception.ErrorKind;

/**
 * market 域错误码（商品市场/订单/争议等）。
 *
 * <p>约定：ErrorKind 表达稳定类别，Web adapter 映射 HTTP status。</p>
 */
public enum MarketErrorCode implements ErrorCode {

    REQUEST_REPLAY_CONFLICT(18001, "请求号与已有市场订单不一致", ErrorKind.CONFLICT),
    ORDER_TRANSITION_CONFLICT(18002, "市场订单状态已发生变化", ErrorKind.CONFLICT);

    private final int code;
    private final String message;
    private final ErrorKind kind;

    MarketErrorCode(int code, String message, ErrorKind kind) {
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
