package com.nowcoder.community.common.exception;

public enum ErrorKind {
    INVALID_INPUT(400),
    UNAUTHENTICATED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    CONFLICT(409),
    THROTTLED(429),
    UNAVAILABLE(503),
    INTERNAL(500);

    private final int httpStatus;

    ErrorKind(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    /**
     * HTTP status used by both the servlet and the reactive error-handling stacks.
     */
    public int statusOf() {
        return httpStatus;
    }
}
