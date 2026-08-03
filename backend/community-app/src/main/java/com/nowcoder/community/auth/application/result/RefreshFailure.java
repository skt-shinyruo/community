package com.nowcoder.community.auth.application.result;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.ErrorCode;

public class RefreshFailure extends BusinessException {

    public RefreshFailure(ErrorCode errorCode) {
        super(errorCode);
    }

    public RefreshFailure(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
