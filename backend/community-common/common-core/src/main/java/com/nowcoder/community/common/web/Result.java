package com.nowcoder.community.common.web;

public class Result<T> {

    private int code;
    private String message;
    /**
     * HTTP adapter 写入的传输语义；不得从业务 code 数值推断。
     */
    private int httpStatus;
    private T data;
    private String traceId;
    private long timestamp;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "OK";
        r.httpStatus = 200;
        r.data = data;
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static Result<Void> ok() {
        return ok(null);
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
