package com.nowcoder.community.common.web;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ResultTraceIdAdvice advice = new ResultTraceIdAdvice();

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void businessExceptionShouldKeepHttpStatusCodeAndTraceId() {
        TraceId.set("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        ResponseEntity<Result<Void>> resp = handler.handleBusiness(new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "bad"));
        advice.beforeBodyWrite(resp.getBody(), null, null, null, null, null);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(400);
        assertThat(resp.getBody().getMessage()).isEqualTo("bad");
        assertThat(resp.getBody().getTraceId()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }

    @Test
    void serverErrorBusinessExceptionShouldReturnInternalError() {
        TraceId.set("22222222222222222222222222222222");

        ResponseEntity<Result<Void>> response = handler.handleBusiness(new BusinessException(CommonErrorCode.INTERNAL_ERROR, "server exploded"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getCode());

    }

    @Test
    void validationExceptionShouldBeInvalidArgumentWithTraceId() {
        TraceId.set("cccccccccccccccccccccccccccccccc");
        ResponseEntity<Result<Void>> resp = handler.handleValidation(new ConstraintViolationException("x", Set.of()));
        advice.beforeBodyWrite(resp.getBody(), null, null, null, null, null);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT.getCode());
        assertThat(resp.getBody().getTraceId()).isEqualTo("cccccccccccccccccccccccccccccccc");
    }

    @Test
    void unknownExceptionShouldBeInternalErrorWithTraceId() {
        TraceId.set("dddddddddddddddddddddddddddddddd");
        RuntimeException ex = new RuntimeException("boom") {
            @Override
            public synchronized Throwable fillInStackTrace() {
                return this;
            }
        };
        ResponseEntity<Result<Void>> resp = handler.handleGeneric(ex);
        advice.beforeBodyWrite(resp.getBody(), null, null, null, null, null);

        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getCode());
        assertThat(resp.getBody().getTraceId()).isEqualTo("dddddddddddddddddddddddddddddddd");
    }

    @Test
    void dataAccessExceptionShouldBeServiceUnavailable() {
        TraceId.set("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
        DataAccessException ex = new DataAccessException("db down") {
            @Override
            public synchronized Throwable fillInStackTrace() {
                return this;
            }
        };
        ResponseEntity<Result<Void>> resp = handler.handleDataAccess(ex);
        advice.beforeBodyWrite(resp.getBody(), null, null, null, null, null);

        assertThat(resp.getStatusCode().value()).isEqualTo(503);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE.getCode());
        assertThat(resp.getBody().getTraceId()).isEqualTo("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");

    }

    @Test
    void missingRequestParamShouldBe400WithTraceId() {
        TraceId.set("ffffffffffffffffffffffffffffffff");
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("ip", "String");
        ResponseEntity<Result<Void>> resp = handler.handleRequestParam(ex);
        advice.beforeBodyWrite(resp.getBody(), null, null, null, null, null);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(400);
        assertThat(resp.getBody().getTraceId()).isEqualTo("ffffffffffffffffffffffffffffffff");
    }

}
