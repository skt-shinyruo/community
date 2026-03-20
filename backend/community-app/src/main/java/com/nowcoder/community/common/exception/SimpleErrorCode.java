package com.nowcoder.community.common.exception;

/**
 * 运行期动态错误码（用于跨服务转发/透传 Result.code/message）。
 */
public class SimpleErrorCode implements ErrorCode {

    private final int code;
    private final String message;
    private final int httpStatus;

    public SimpleErrorCode(int code, String message) {
        this(code, message, defaultHttpStatus(code));
    }

    public SimpleErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }

    private static int defaultHttpStatus(int code) {
        if (code >= 400 && code < 600) {
            return code;
        }
        return 500;
    }
}
