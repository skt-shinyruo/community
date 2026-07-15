package com.nowcoder.community.common.webflux;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.web.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode() == null ? CommonErrorCode.INTERNAL_ERROR : ex.getErrorCode();
        String message = ex.getMessage() == null ? errorCode.getMessage() : ex.getMessage();
        return response(errorCode, message);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleResponseStatus(ResponseStatusException ex) {
        if (ex == null || ex.getStatusCode() == null) {
            return response(CommonErrorCode.INTERNAL_ERROR);
        }
        int status = ex.getStatusCode().value();
        if (status == ErrorKindHttpStatusMapper.statusOf(CommonErrorCode.UNAUTHORIZED.getKind())) {
            return response(CommonErrorCode.UNAUTHORIZED);
        }
        if (status == ErrorKindHttpStatusMapper.statusOf(CommonErrorCode.FORBIDDEN.getKind())) {
            return response(CommonErrorCode.FORBIDDEN);
        }
        if (status == ErrorKindHttpStatusMapper.statusOf(CommonErrorCode.NOT_FOUND.getKind())) {
            return response(CommonErrorCode.NOT_FOUND);
        }
        return ResponseEntity.status(status)
                .body(Result.error(status, ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason(), status));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGeneric(Exception ex) {
        return response(CommonErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<Result<Void>> response(ErrorCode errorCode) {
        return response(errorCode, errorCode.getMessage());
    }

    private ResponseEntity<Result<Void>> response(ErrorCode errorCode, String message) {
        int status = ErrorKindHttpStatusMapper.statusOf(errorCode.getKind());
        return ResponseEntity.status(status)
                .body(Result.error(errorCode.getCode(), message, status));
    }
}
