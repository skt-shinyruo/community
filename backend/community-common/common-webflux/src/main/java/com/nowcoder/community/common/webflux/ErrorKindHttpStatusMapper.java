package com.nowcoder.community.common.webflux;

import com.nowcoder.community.common.exception.ErrorKind;

final class ErrorKindHttpStatusMapper {

    private ErrorKindHttpStatusMapper() {
    }

    static int statusOf(ErrorKind kind) {
        if (kind == null) {
            return 500;
        }
        return switch (kind) {
            case INVALID_INPUT -> 400;
            case UNAUTHENTICATED -> 401;
            case FORBIDDEN -> 403;
            case NOT_FOUND -> 404;
            case CONFLICT -> 409;
            case THROTTLED -> 429;
            case UNAVAILABLE -> 503;
            case INTERNAL -> 500;
        };
    }
}
