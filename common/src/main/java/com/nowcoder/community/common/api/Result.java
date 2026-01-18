package com.nowcoder.community.common.api;

import com.nowcoder.community.common.trace.TraceId;

public class Result<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;
    private long timestamp;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = CommonErrorCode.OK.getCode();
        r.message = CommonErrorCode.OK.getMessage();
        r.data = data;
        r.traceId = TraceId.get();
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
        r.traceId = TraceId.get();
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        r.traceId = TraceId.get();
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
