package com.nowcoder.community.im.core.web;

import com.nowcoder.community.im.core.api.CommonErrorCode;
import com.nowcoder.community.im.core.api.Result;
import com.nowcoder.community.im.core.trace.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(httpStatusOf(CommonErrorCode.FORBIDDEN.getHttpStatus()))
                .body(Result.error(CommonErrorCode.FORBIDDEN));
    }

    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<Result<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(httpStatusOf(CommonErrorCode.INVALID_ARGUMENT.getHttpStatus()))
                .body(Result.error(CommonErrorCode.INVALID_ARGUMENT));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result<Void>> handleRequestParam(Exception e) {
        return ResponseEntity.status(httpStatusOf(CommonErrorCode.INVALID_ARGUMENT.getHttpStatus()))
                .body(Result.error(CommonErrorCode.INVALID_ARGUMENT));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleBadJson(HttpMessageNotReadableException e) {
        return ResponseEntity.status(httpStatusOf(CommonErrorCode.INVALID_ARGUMENT.getHttpStatus()))
                .body(Result.error(CommonErrorCode.INVALID_ARGUMENT));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(httpStatusOf(405))
                .body(Result.error(405, "请求方法不支持"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(httpStatusOf(415))
                .body(Result.error(415, "不支持的 Content-Type"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleResponseStatus(ResponseStatusException e) {
        if (e == null || e.getStatusCode() == null) {
            return ResponseEntity.status(httpStatusOf(CommonErrorCode.INTERNAL_ERROR.getHttpStatus()))
                    .body(Result.error(CommonErrorCode.INTERNAL_ERROR));
        }
        int status = e.getStatusCode().value();
        String reason = e.getReason();
        if (status == CommonErrorCode.INVALID_ARGUMENT.getHttpStatus()) {
            return ResponseEntity.status(httpStatusOf(status))
                    .body(Result.error(status, reason == null ? CommonErrorCode.INVALID_ARGUMENT.getMessage() : reason, status));
        }
        if (status == CommonErrorCode.UNAUTHORIZED.getHttpStatus()) {
            return ResponseEntity.status(httpStatusOf(status))
                    .body(Result.error(CommonErrorCode.UNAUTHORIZED));
        }
        if (status == CommonErrorCode.FORBIDDEN.getHttpStatus()) {
            return ResponseEntity.status(httpStatusOf(status))
                    .body(Result.error(CommonErrorCode.FORBIDDEN));
        }
        if (status == CommonErrorCode.NOT_FOUND.getHttpStatus()) {
            return ResponseEntity.status(httpStatusOf(status))
                    .body(Result.error(status, reason == null ? CommonErrorCode.NOT_FOUND.getMessage() : reason, status));
        }
        return ResponseEntity.status(httpStatusOf(status))
                .body(Result.error(status, reason == null ? e.getStatusCode().toString() : reason, status));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> handleDataAccess(DataAccessException e) {
        log.error("[im-core][data-access] traceId={}", TraceId.get(), e);
        return ResponseEntity.status(httpStatusOf(CommonErrorCode.SERVICE_UNAVAILABLE.getHttpStatus()))
                .body(Result.error(CommonErrorCode.SERVICE_UNAVAILABLE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGeneric(Exception e) {
        log.error("[im-core][unhandled] traceId={}", TraceId.get(), e);
        return ResponseEntity.status(httpStatusOf(CommonErrorCode.INTERNAL_ERROR.getHttpStatus()))
                .body(Result.error(CommonErrorCode.INTERNAL_ERROR));
    }

    private HttpStatus httpStatusOf(int httpStatus) {
        try {
            return HttpStatus.valueOf(httpStatus);
        } catch (IllegalArgumentException ignore) {
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}

