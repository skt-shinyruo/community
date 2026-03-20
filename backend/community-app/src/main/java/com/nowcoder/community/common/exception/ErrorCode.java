package com.nowcoder.community.common.exception;

public interface ErrorCode {
    int getCode();

    String getMessage();

    int getHttpStatus();
}
