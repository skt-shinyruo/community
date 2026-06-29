package com.nowcoder.community.auth.application.result;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.ErrorCode;

public class RefreshFailure extends BusinessException {

    private final boolean clearRefreshCookie;

    public RefreshFailure(ErrorCode errorCode, boolean clearRefreshCookie) {
        super(errorCode);
        this.clearRefreshCookie = clearRefreshCookie;
    }

    public RefreshFailure(ErrorCode errorCode, String message, Throwable cause, boolean clearRefreshCookie) {
        super(errorCode, message, cause);
        this.clearRefreshCookie = clearRefreshCookie;
    }

    public boolean clearRefreshCookie() {
        return clearRefreshCookie;
    }
}
