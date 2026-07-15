package com.nowcoder.community.common.web;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.logging.EventLogFields;
import com.nowcoder.community.common.trace.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
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
    private static final String CATEGORY = "exception";
    private static final String MDC_CATEGORY = EventLogFields.EVENT_CATEGORY;
    private static final String MDC_ACTION = EventLogFields.EVENT_ACTION;
    private static final String MDC_OUTCOME = EventLogFields.EVENT_OUTCOME;

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        return response(CommonErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode() == null ? CommonErrorCode.INTERNAL_ERROR : e.getErrorCode();
        int status = ErrorKindHttpStatusMapper.statusOf(errorCode.getKind());
        String message = e.getMessage();
        if (message == null) {
            message = errorCode.getMessage();
        }
        if (status >= 500) {
            String resolvedMessage = message;
            errorEvent("business_exception", () -> log.error(
                    "[exception][business] traceId={} code={} status={} message={}",
                    TraceId.get(), errorCode.getCode(), status, resolvedMessage, e
            ));
        }
        return response(errorCode, message);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class})
    public ResponseEntity<Result<Void>> handleValidation(Exception e) {
        return response(CommonErrorCode.INVALID_ARGUMENT);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleBadJson(HttpMessageNotReadableException e) {
        return response(CommonErrorCode.INVALID_ARGUMENT);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result<Void>> handleRequestParam(Exception e) {
        return response(CommonErrorCode.INVALID_ARGUMENT);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(httpStatusOf(405))
                .body(Result.error(405, "请求方法不支持", 405));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(httpStatusOf(415))
                .body(Result.error(415, "不支持的 Content-Type", 415));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Result<Void>> handleNotFound(Exception e) {
        return response(CommonErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleResponseStatus(ResponseStatusException e) {
        if (e == null || e.getStatusCode() == null) {
            return response(CommonErrorCode.INTERNAL_ERROR);
        }
        int status = e.getStatusCode().value();
        if (status == ErrorKindHttpStatusMapper.statusOf(CommonErrorCode.INVALID_ARGUMENT.getKind())) {
            return response(CommonErrorCode.INVALID_ARGUMENT);
        }
        if (status == ErrorKindHttpStatusMapper.statusOf(CommonErrorCode.UNAUTHORIZED.getKind())) {
            return response(CommonErrorCode.UNAUTHORIZED);
        }
        if (status == ErrorKindHttpStatusMapper.statusOf(CommonErrorCode.FORBIDDEN.getKind())) {
            return response(CommonErrorCode.FORBIDDEN);
        }
        if (status == ErrorKindHttpStatusMapper.statusOf(CommonErrorCode.NOT_FOUND.getKind())) {
            return response(CommonErrorCode.NOT_FOUND);
        }
        return ResponseEntity.status(httpStatusOf(status))
                .body(Result.error(
                        status,
                        e.getReason() == null ? e.getStatusCode().toString() : e.getReason(),
                        status
                ));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> handleDataAccess(DataAccessException e) {
        errorEvent("data_access_exception", () -> log.error("[exception][data-access] traceId={}", TraceId.get(), e));
        return response(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGeneric(Exception e) {
        errorEvent("unhandled_exception", () -> log.error("[exception][unhandled] traceId={}", TraceId.get(), e));
        return response(CommonErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<Result<Void>> response(ErrorCode errorCode) {
        return response(errorCode, errorCode.getMessage());
    }

    private ResponseEntity<Result<Void>> response(ErrorCode errorCode, String message) {
        int status = ErrorKindHttpStatusMapper.statusOf(errorCode.getKind());
        Result<Void> body = Result.error(errorCode.getCode(), message, status);
        return ResponseEntity.status(httpStatusOf(status)).body(body);
    }

    private HttpStatus httpStatusOf(int httpStatus) {
        try {
            return HttpStatus.valueOf(httpStatus);
        } catch (IllegalArgumentException ignored) {
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private void errorEvent(String action, Runnable logAction) {
        String previousCategory = MDC.get(MDC_CATEGORY);
        String previousAction = MDC.get(MDC_ACTION);
        String previousOutcome = MDC.get(MDC_OUTCOME);
        MDC.put(MDC_CATEGORY, CATEGORY);
        MDC.put(MDC_ACTION, action);
        MDC.put(MDC_OUTCOME, "failure");
        try {
            logAction.run();
        } finally {
            restore(MDC_CATEGORY, previousCategory);
            restore(MDC_ACTION, previousAction);
            restore(MDC_OUTCOME, previousOutcome);
        }
    }

    private void restore(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, previousValue);
    }
}
