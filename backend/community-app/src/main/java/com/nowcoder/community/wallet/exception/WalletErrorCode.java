package com.nowcoder.community.wallet.exception;

import com.nowcoder.community.common.exception.ErrorCode;

public enum WalletErrorCode implements ErrorCode {

    INVALID_REQUEST(17001, "钱包请求参数错误", 400),
    ACCOUNT_NOT_FOUND(17002, "钱包账户不存在", 404),
    TXN_NOT_BALANCED(17003, "钱包交易分录不平衡", 409),
    ACCOUNT_BALANCE_INSUFFICIENT(17004, "钱包余额不足", 409),
    ACCOUNT_UPDATE_CONFLICT(17005, "钱包账户更新冲突", 409);

    private final int code;
    private final String message;
    private final int httpStatus;

    WalletErrorCode(int code, String message, int httpStatus) {
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
