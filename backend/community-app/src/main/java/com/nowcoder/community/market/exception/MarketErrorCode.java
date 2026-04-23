package com.nowcoder.community.market.exception;

import com.nowcoder.community.common.exception.ErrorCode;

/**
 * market 域错误码（商品市场/订单/争议等）。
 *
 * <p>约定：HTTP status 表达“错误类别”，Result.code 表达“业务细分”。</p>
 */
public enum MarketErrorCode implements ErrorCode {

    REQUEST_REPLAY_CONFLICT(18001, "请求号与已有市场订单不一致", 409);

    private final int code;
    private final String message;
    private final int httpStatus;

    MarketErrorCode(int code, String message, int httpStatus) {
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
