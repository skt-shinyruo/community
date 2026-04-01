package com.nowcoder.community.common.webflux;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.web.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleResponseStatus(ResponseStatusException ex) {
        if (ex == null || ex.getStatusCode() == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error(CommonErrorCode.INTERNAL_ERROR));
        }
        int status = ex.getStatusCode().value();
        if (status == CommonErrorCode.UNAUTHORIZED.getHttpStatus()) {
            return ResponseEntity.status(status).body(Result.error(CommonErrorCode.UNAUTHORIZED));
        }
        if (status == CommonErrorCode.FORBIDDEN.getHttpStatus()) {
            return ResponseEntity.status(status).body(Result.error(CommonErrorCode.FORBIDDEN));
        }
        if (status == CommonErrorCode.NOT_FOUND.getHttpStatus()) {
            return ResponseEntity.status(status).body(Result.error(CommonErrorCode.NOT_FOUND));
        }
        return ResponseEntity.status(status)
                .body(Result.error(status, ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason(), status));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(CommonErrorCode.INTERNAL_ERROR));
    }
}
