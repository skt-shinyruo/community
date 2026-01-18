package com.nowcoder.community.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.trace.TraceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {

    @AfterEach
    void tearDown() {
        TraceId.clear();
    }

    @Test
    void okShouldCarryTraceId() {
        TraceId.set("t1");
        Result<String> r = Result.ok("hello");
        assertThat(r.getCode()).isEqualTo(0);
        assertThat(r.getData()).isEqualTo("hello");
        assertThat(r.getTraceId()).isEqualTo("t1");
        assertThat(r.getTimestamp()).isGreaterThan(0);
    }

    @Test
    void shouldSerializeToJson() throws Exception {
        TraceId.set("t2");
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(Result.ok("x"));
        assertThat(json).contains("\"code\":0");
        assertThat(json).contains("\"data\":\"x\"");
        assertThat(json).contains("\"traceId\":\"t2\"");
        assertThat(json).contains("\"timestamp\":");
    }
}
