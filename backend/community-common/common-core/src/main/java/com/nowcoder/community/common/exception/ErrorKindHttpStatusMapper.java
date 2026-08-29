package com.nowcoder.community.common.exception;

/**
 * Maps {@link ErrorKind} to the HTTP status used by both the servlet and the
 * reactive error-handling stacks.
 */
public final class ErrorKindHttpStatusMapper {

    private ErrorKindHttpStatusMapper() {
    }

    public static int statusOf(ErrorKind kind) {
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
