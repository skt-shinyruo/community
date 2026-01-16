package com.nowcoder.community.common.web;

import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.ErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(CommonErrorCode.BAD_REQUEST));
        }

        return ResponseEntity.status(resolveHttpStatus(errorCode)).body(Result.fail(errorCode.code(), e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<Void>> handleBindException(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(CommonErrorCode.BAD_REQUEST));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        logger.error("未捕获异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(CommonErrorCode.INTERNAL_ERROR));
    }

    private HttpStatus resolveHttpStatus(ErrorCode errorCode) {
        int code = errorCode.code();
        if (code >= 40100 && code < 40200) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code >= 40300 && code < 40400) {
            return HttpStatus.FORBIDDEN;
        }
        if (code >= 40400 && code < 40500) {
            return HttpStatus.NOT_FOUND;
        }
        if (code >= 40000 && code < 50000) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
