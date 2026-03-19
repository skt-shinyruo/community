package com.nowcoder.community.infra.web;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.infra.trace.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode() == null ? CommonErrorCode.INTERNAL_ERROR : e.getErrorCode();
        int status = errorCode.getHttpStatus();
        String message = e.getMessage();
        if (message == null) {
            message = errorCode.getMessage();
        }
        if (status >= 500) {
            log.error("[exception][business] traceId={} code={} status={} message={}", TraceId.get(), errorCode.getCode(), status, message, e);
        }
        Result<Void> body = Result.error(errorCode.getCode(), message, errorCode.getHttpStatus());
        return ResponseEntity.status(httpStatusOf(errorCode.getHttpStatus())).body(body);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class})
    public ResponseEntity<Result<Void>> handleValidation(Exception e) {
        return ResponseEntity.status(httpStatusOf(CommonErrorCode.INVALID_ARGUMENT.getHttpStatus()))
                .body(Result.error(CommonErrorCode.INVALID_ARGUMENT));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleBadJson(HttpMessageNotReadableException e) {
        return ResponseEntity.status(httpStatusOf(CommonErrorCode.INVALID_ARGUMENT.getHttpStatus()))
                .body(Result.error(CommonErrorCode.INVALID_ARGUMENT));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result<Void>> handleRequestParam(Exception e) {
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

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Result<Void>> handleNotFound(Exception e) {
        return ResponseEntity.status(httpStatusOf(CommonErrorCode.NOT_FOUND.getHttpStatus()))
                .body(Result.error(CommonErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleResponseStatus(ResponseStatusException e) {
        if (e == null || e.getStatusCode() == null) {
            return ResponseEntity.status(httpStatusOf(CommonErrorCode.INTERNAL_ERROR.getHttpStatus()))
                    .body(Result.error(CommonErrorCode.INTERNAL_ERROR));
        }
        int status = e.getStatusCode().value();
        if (status == CommonErrorCode.INVALID_ARGUMENT.getHttpStatus()) {
            return ResponseEntity.status(httpStatusOf(status))
                    .body(Result.error(CommonErrorCode.INVALID_ARGUMENT));
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
                    .body(Result.error(CommonErrorCode.NOT_FOUND));
        }
        return ResponseEntity.status(httpStatusOf(status))
                .body(Result.error(status, e.getReason() == null ? e.getStatusCode().toString() : e.getReason()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> handleDataAccess(DataAccessException e) {
        log.error("[exception][data-access] traceId={}", TraceId.get(), e);
        return ResponseEntity.status(httpStatusOf(CommonErrorCode.SERVICE_UNAVAILABLE.getHttpStatus()))
                .body(Result.error(CommonErrorCode.SERVICE_UNAVAILABLE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGeneric(Exception e) {
        log.error("[exception][unhandled] traceId={}", TraceId.get(), e);
        return ResponseEntity.status(httpStatusOf(CommonErrorCode.INTERNAL_ERROR.getHttpStatus()))
                .body(Result.error(CommonErrorCode.INTERNAL_ERROR));
    }

    private HttpStatus httpStatusOf(int httpStatus) {
        try {
            return HttpStatus.valueOf(httpStatus);
        } catch (IllegalArgumentException ignored) {
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
