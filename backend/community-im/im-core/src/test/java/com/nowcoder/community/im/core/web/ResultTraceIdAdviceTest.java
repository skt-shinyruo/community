package com.nowcoder.community.im.core.web;

import com.nowcoder.community.im.core.trace.TraceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

class ResultTraceIdAdviceTest {

    @AfterEach
    void tearDown() {
        TraceId.clear();
    }

    @Test
    void adviceShouldWrapPlainBodyIntoResultAndFillTraceId() throws Exception {
        TraceId.set("im-t1");
        SamplePayload payload = new SamplePayload("hello");

        ResultTraceIdAdvice advice = new ResultTraceIdAdvice();
        Object body = advice.beforeBodyWrite(payload, returnType("plainPayload"), null, null, null, null);

        assertThat(body).isInstanceOf(Result.class);
        Result<?> result = (Result<?>) body;
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isSameAs(payload);
        assertThat(result.getTraceId()).isEqualTo("im-t1");
    }

    @Test
    void supportsShouldKeepResponseEntityResultResponsesEnabled() throws Exception {
        ResultTraceIdAdvice advice = new ResultTraceIdAdvice();

        assertThat(advice.supports(returnType("wrappedResponse"), null)).isTrue();
    }

    @Test
    void supportsShouldSkipStringResponses() throws Exception {
        ResultTraceIdAdvice advice = new ResultTraceIdAdvice();

        assertThat(advice.supports(returnType("plainString"), StringHttpMessageConverter.class)).isFalse();
    }

    @Test
    void supportsShouldSkipVoidResponses() throws Exception {
        ResultTraceIdAdvice advice = new ResultTraceIdAdvice();

        assertThat(advice.supports(returnType("noContent"), StringHttpMessageConverter.class)).isFalse();
    }

    private static MethodParameter returnType(String methodName) throws NoSuchMethodException {
        return new MethodParameter(SampleReturnTypes.class.getMethod(methodName), -1);
    }

    static class SampleReturnTypes {

        public SamplePayload plainPayload() {
            return null;
        }

        public ResponseEntity<Result<String>> wrappedResponse() {
            return ResponseEntity.ok(Result.ok("x"));
        }

        public String plainString() {
            return "";
        }

        public void noContent() {
        }
    }

    static class SamplePayload {

        private final String value;

        SamplePayload(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
