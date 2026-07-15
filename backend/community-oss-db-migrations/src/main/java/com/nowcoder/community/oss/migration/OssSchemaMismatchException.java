package com.nowcoder.community.oss.migration;

public final class OssSchemaMismatchException extends IllegalStateException {

    OssSchemaMismatchException(String message) {
        super(message);
    }

    OssSchemaMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
