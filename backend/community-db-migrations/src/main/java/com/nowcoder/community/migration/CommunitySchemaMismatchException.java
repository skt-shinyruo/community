package com.nowcoder.community.migration;

public final class CommunitySchemaMismatchException extends IllegalStateException {

    CommunitySchemaMismatchException(String message) {
        super(message);
    }

    CommunitySchemaMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
