package com.nowcoder.community.common.api;

import com.nowcoder.community.common.trace.TraceId;

import java.time.Instant;

public class Result<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;
    private long timestamp;

    public Result() {
    }

    public Result(int code, String message, T data, String traceId, long timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
        this.timestamp = timestamp;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(CommonErrorCode.SUCCESS.code(), CommonErrorCode.SUCCESS.message(), data, TraceId.currentOrNull(), Instant.now().toEpochMilli());
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static Result<Void> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.code(), errorCode.message(), null, TraceId.currentOrNull(), Instant.now().toEpochMilli());
    }

    public static Result<Void> fail(int code, String message) {
        return new Result<>(code, message, null, TraceId.currentOrNull(), Instant.now().toEpochMilli());
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

