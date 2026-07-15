package com.nowcoder.community.common.exception;

/**
 * 运行期动态错误码（用于跨服务转发/透传 Result.code/message）。
 */
public class SimpleErrorCode implements ErrorCode {

    private final int code;
    private final String message;
    private final ErrorKind kind;

    public SimpleErrorCode(int code, String message, ErrorKind kind) {
        this.code = code;
        this.message = message;
        this.kind = kind;
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
    public ErrorKind getKind() {
        return kind;
    }
}
