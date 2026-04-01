package com.nowcoder.community.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.trace.TraceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {

    @AfterEach
    void tearDown() {
        TraceId.clear();
    }

    @Test
    void okShouldNotCarryTraceIdByDefault() {
        TraceId.set("t1");
        Result<String> r = Result.ok("hello");
        assertThat(r.getCode()).isEqualTo(0);
        assertThat(r.getData()).isEqualTo("hello");
        assertThat(r.getTraceId()).isNull();
        assertThat(r.getTimestamp()).isGreaterThan(0);
    }

    @Test
    void adviceShouldFillTraceIdWhenMissing() {
        TraceId.set("1234567890abcdef1234567890abcdef");
        Result<String> r = Result.ok("x");
        assertThat(r.getTraceId()).isNull();

        ResultTraceIdAdvice advice = new ResultTraceIdAdvice();
        advice.beforeBodyWrite(r, null, null, null, null, null);

        assertThat(r.getTraceId()).isEqualTo("1234567890abcdef1234567890abcdef");
    }

    @Test
    void adviceShouldWrapPlainBodyIntoResultAndFillTraceId() throws Exception {
        TraceId.set("abcdefabcdefabcdefabcdefabcdefab");
        SamplePayload payload = new SamplePayload("hello");

        ResultTraceIdAdvice advice = new ResultTraceIdAdvice();
        Object body = advice.beforeBodyWrite(payload, returnType("plainPayload"), null, null, null, null);

        assertThat(body).isInstanceOf(Result.class);
        Result<?> result = (Result<?>) body;
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isSameAs(payload);
        assertThat(result.getTraceId()).isEqualTo("abcdefabcdefabcdefabcdefabcdefab");
    }

    @Test
    void supportsShouldSkipStringResponses() throws Exception {
        ResultTraceIdAdvice advice = new ResultTraceIdAdvice();

        assertThat(advice.supports(returnType("plainString"), StringHttpMessageConverter.class)).isFalse();
    }

    @Test
    void supportsShouldSkipResponseEntityResponses() throws Exception {
        ResultTraceIdAdvice advice = new ResultTraceIdAdvice();

        assertThat(advice.supports(returnType("resourceResponse"), StringHttpMessageConverter.class)).isFalse();
    }

    @Test
    void supportsShouldKeepResponseEntityResultResponsesEnabled() throws Exception {
        ResultTraceIdAdvice advice = new ResultTraceIdAdvice();

        assertThat(advice.supports(returnType("wrappedResponse"), null)).isTrue();
    }

    @Test
    void supportsShouldSkipVoidResponses() throws Exception {
        ResultTraceIdAdvice advice = new ResultTraceIdAdvice();

        assertThat(advice.supports(returnType("noContent"), StringHttpMessageConverter.class)).isFalse();
    }

    @Test
    void shouldSerializeToJson() throws Exception {
        TraceId.set("t3");
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(Result.ok("x"));
        assertThat(json).contains("\"code\":0");
        assertThat(json).contains("\"data\":\"x\"");
        assertThat(json).contains("\"traceId\":null");
        assertThat(json).contains("\"timestamp\":");
    }

    private static MethodParameter returnType(String methodName) throws NoSuchMethodException {
        return new MethodParameter(SampleReturnTypes.class.getMethod(methodName), -1);
    }

    static class SampleReturnTypes {

        public SamplePayload plainPayload() {
            return null;
        }

        public String plainString() {
            return "";
        }

        public ResponseEntity<Resource> resourceResponse() {
            return ResponseEntity.ok().build();
        }

        public ResponseEntity<Result<String>> wrappedResponse() {
            return ResponseEntity.ok(Result.ok("x"));
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
