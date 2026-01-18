package com.nowcoder.community.common.web;

import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        int code = e.getErrorCode() == null ? CommonErrorCode.INTERNAL_ERROR.getCode() : e.getErrorCode().getCode();
        Result<Void> body = Result.error(code, e.getMessage());
        return ResponseEntity.status(httpStatusOf(code)).body(body);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<Void>> handleValidation(Exception e) {
        return ResponseEntity.status(httpStatusOf(CommonErrorCode.INVALID_ARGUMENT.getCode()))
                .body(Result.error(CommonErrorCode.INVALID_ARGUMENT));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleBadJson(HttpMessageNotReadableException e) {
        return ResponseEntity.status(httpStatusOf(CommonErrorCode.INVALID_ARGUMENT.getCode()))
                .body(Result.error(CommonErrorCode.INVALID_ARGUMENT));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGeneric(Exception e) {
        return ResponseEntity.status(httpStatusOf(CommonErrorCode.INTERNAL_ERROR.getCode()))
                .body(Result.error(CommonErrorCode.INTERNAL_ERROR));
    }

    private HttpStatus httpStatusOf(int code) {
        if (code >= 400 && code < 600) {
            try {
                return HttpStatus.valueOf(code);
            } catch (Exception ignored) {
            }
        }
        return HttpStatus.OK;
    }
}
