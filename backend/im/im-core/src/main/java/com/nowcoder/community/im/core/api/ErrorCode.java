package com.nowcoder.community.im.core.api;

public interface ErrorCode {

    int getCode();

    String getMessage();

    int getHttpStatus();
}

