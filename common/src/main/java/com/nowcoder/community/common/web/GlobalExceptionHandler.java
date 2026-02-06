package com.nowcoder.community.common.web;

import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.ErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode() == null ? CommonErrorCode.INTERNAL_ERROR : e.getErrorCode();
        String message = e.getMessage();
        if (message == null) {
            message = errorCode.getMessage();
        }
        Result<Void> body = Result.error(errorCode.getCode(), message);
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

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> handleDataAccess(DataAccessException e) {
        return ResponseEntity.status(httpStatusOf(CommonErrorCode.SERVICE_UNAVAILABLE.getHttpStatus()))
                .body(Result.error(CommonErrorCode.SERVICE_UNAVAILABLE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGeneric(Exception e) {
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
