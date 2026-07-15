package com.nowcoder.community.oss.client;

public final class OssClientException extends IllegalStateException {

    private final Category category;
    private final int httpStatus;
    private final boolean retryable;

    public OssClientException(
            Category category,
            int httpStatus,
            boolean retryable,
            String message
    ) {
        this(category, httpStatus, retryable, message, null);
    }

    public OssClientException(
            Category category,
            int httpStatus,
            boolean retryable,
            String message,
            Throwable cause
    ) {
        super(message == null || message.isBlank() ? "OSS request failed" : message, cause);
        this.category = category == null ? Category.BAD_RESPONSE : category;
        this.httpStatus = Math.max(0, httpStatus);
        this.retryable = retryable;
    }

    public Category category() {
        return category;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public boolean retryable() {
        return retryable;
    }

    public enum Category {
        NOT_FOUND,
        CONFLICT,
        TRANSIENT,
        TIMEOUT,
        BAD_RESPONSE
    }
}
