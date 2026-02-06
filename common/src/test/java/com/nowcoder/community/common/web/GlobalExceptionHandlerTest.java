package com.nowcoder.community.common.web;

import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.trace.TraceId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void tearDown() {
        TraceId.clear();
    }

    @Test
    void businessExceptionShouldKeepHttpStatusCodeAndTraceId() {
        TraceId.set("t-err-1");
        ResponseEntity<Result<Void>> resp = handler.handleBusiness(new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "bad"));

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

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT.getCode());
        assertThat(resp.getBody().getTraceId()).isEqualTo("t-err-2");
    }

    @Test
    void unknownExceptionShouldBeInternalErrorWithTraceId() {
        TraceId.set("t-err-3");
        ResponseEntity<Result<Void>> resp = handler.handleGeneric(new RuntimeException("boom"));

        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getCode());
        assertThat(resp.getBody().getTraceId()).isEqualTo("t-err-3");
    }

    @Test
    void missingRequestParamShouldBe400WithTraceId() {
        TraceId.set("t-err-4");
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("ip", "String");
        ResponseEntity<Result<Void>> resp = handler.handleRequestParam(ex);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(400);
        assertThat(resp.getBody().getTraceId()).isEqualTo("t-err-4");
    }
}
