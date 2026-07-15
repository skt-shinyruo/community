package com.nowcoder.community.im.migration;

final class ImSchemaMismatchException extends IllegalStateException {

    ImSchemaMismatchException(String message) {
        super(message);
    }

    ImSchemaMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
