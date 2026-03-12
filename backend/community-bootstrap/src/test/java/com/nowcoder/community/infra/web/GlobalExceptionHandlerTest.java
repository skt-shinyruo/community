package com.nowcoder.community.infra.web;

import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.infra.trace.TraceId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ResultTraceIdAdvice advice = new ResultTraceIdAdvice();

    @AfterEach
    void tearDown() {
        TraceId.clear();
    }

    @Test
    void businessExceptionShouldKeepHttpStatusCodeAndTraceId() {
        TraceId.set("t-err-1");
        ResponseEntity<Result<Void>> resp = handler.handleBusiness(new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "bad"));
        advice.beforeBodyWrite(resp.getBody(), null, null, null, null, null);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(400);
        assertThat(resp.getBody().getMessage()).isEqualTo("bad");
        assertThat(resp.getBody().getTraceId()).isEqualTo("t-err-1");
    }

    @Test
    void validationExceptionShouldBeInvalidArgumentWithTraceId() {
        TraceId.set("t-err-2");
        ResponseEntity<Result<Void>> resp = handler.handleValidation(new ConstraintViolationException("x", Set.of()));
        advice.beforeBodyWrite(resp.getBody(), null, null, null, null, null);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT.getCode());
        assertThat(resp.getBody().getTraceId()).isEqualTo("t-err-2");
    }

    @Test
    void unknownExceptionShouldBeInternalErrorWithTraceId() {
        TraceId.set("t-err-3");
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
        assertThat(resp.getBody().getTraceId()).isEqualTo("t-err-3");
    }

    @Test
    void dataAccessExceptionShouldBeServiceUnavailableAndLogged(CapturedOutput output) {
        TraceId.set("t-err-5");
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
        assertThat(resp.getBody().getTraceId()).isEqualTo("t-err-5");
        assertThat(output.getAll()).contains("[exception][data-access] traceId=t-err-5");
    }

    @Test
    void unknownExceptionShouldBeLogged(CapturedOutput output) {
        TraceId.set("t-err-6");
        RuntimeException ex = new RuntimeException("boom-2") {
            @Override
            public synchronized Throwable fillInStackTrace() {
                return this;
            }
        };
        handler.handleGeneric(ex);

        assertThat(output.getAll()).contains("[exception][unhandled] traceId=t-err-6");
    }

    @Test
    void missingRequestParamShouldBe400WithTraceId() {
        TraceId.set("t-err-4");
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("ip", "String");
        ResponseEntity<Result<Void>> resp = handler.handleRequestParam(ex);
        advice.beforeBodyWrite(resp.getBody(), null, null, null, null, null);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(400);
        assertThat(resp.getBody().getTraceId()).isEqualTo("t-err-4");
    }
}
