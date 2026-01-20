package com.nowcoder.community.common.api;

/**
 * 运行期动态错误码（用于跨服务转发/透传 Result.code/message）。
 */
public class SimpleErrorCode implements ErrorCode {

    private final int code;
    private final String message;

    public SimpleErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}

