package com.nowcoder.community.wallet.exception;

import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.exception.ErrorKind;

public enum WalletErrorCode implements ErrorCode {

    INVALID_REQUEST(17001, "钱包请求参数错误", ErrorKind.INVALID_INPUT),
    ACCOUNT_NOT_FOUND(17002, "钱包账户不存在", ErrorKind.NOT_FOUND),
    TXN_NOT_BALANCED(17003, "钱包交易分录不平衡", ErrorKind.CONFLICT),
    ACCOUNT_BALANCE_INSUFFICIENT(17004, "钱包余额不足", ErrorKind.CONFLICT),
    ACCOUNT_UPDATE_CONFLICT(17005, "钱包账户更新冲突", ErrorKind.CONFLICT),
    PLATFORM_CASH_INSUFFICIENT(17006, "平台可提现现金不足", ErrorKind.CONFLICT),
    REQUEST_REPLAY_CONFLICT(17007, "请求号与已有钱包请求不一致", ErrorKind.CONFLICT),
    INVALID_TRANSFER(17008, "转账请求不合法", ErrorKind.INVALID_INPUT),
    ACCOUNT_FROZEN(17009, "钱包账户已冻结", ErrorKind.CONFLICT);

    private final int code;
    private final String message;
    private final ErrorKind kind;

    WalletErrorCode(int code, String message, ErrorKind kind) {
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
