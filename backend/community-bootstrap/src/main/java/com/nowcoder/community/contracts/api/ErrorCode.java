package com.nowcoder.community.contracts.api;

public interface ErrorCode {
    int getCode();

    String getMessage();

    int getHttpStatus();
}

