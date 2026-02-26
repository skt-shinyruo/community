package com.nowcoder.community.platform.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.platform.trace.TraceId;
import com.nowcoder.community.platform.web.ResultTraceIdAdvice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
        TraceId.set("t2");
        Result<String> r = Result.ok("x");
        assertThat(r.getTraceId()).isNull();

        ResultTraceIdAdvice advice = new ResultTraceIdAdvice();
        advice.beforeBodyWrite(r, null, null, null, null, null);

        assertThat(r.getTraceId()).isEqualTo("t2");
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
}
