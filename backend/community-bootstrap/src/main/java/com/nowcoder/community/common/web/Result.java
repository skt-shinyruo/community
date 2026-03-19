package com.nowcoder.community.common.web;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.ErrorCode;

public class Result<T> {

    private int code;
    private String message;
    /**
     * HTTP status 的“语义提示”（可选，默认由 code 推导）。
     *
     * <p>约定：跨服务透传 Result 时，消费者不需要 import 生产方域错误码枚举；若需要还原 HTTP 语义，可使用该字段。</p>
     */
    private int httpStatus;
    private T data;
    private String traceId;
    private long timestamp;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = CommonErrorCode.OK.getCode();
        r.message = CommonErrorCode.OK.getMessage();
        r.httpStatus = CommonErrorCode.OK.getHttpStatus();
        r.data = data;
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        Result<T> r = new Result<>();
        r.code = errorCode.getCode();
        r.message = errorCode.getMessage();
        r.httpStatus = errorCode.getHttpStatus();
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static <T> Result<T> error(int code, String message) {
        return error(code, message, code >= 400 && code < 600 ? code : 500);
    }

    public static <T> Result<T> error(int code, String message, int httpStatus) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        r.httpStatus = httpStatus;
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
