package com.nowcoder.community.im.core.exception;

public interface ErrorCode {

    int getCode();

    String getMessage();

    int getHttpStatus();
}

