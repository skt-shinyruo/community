package com.nowcoder.community.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JsonMappersTest {

    @Test
    void standardMapperShouldSerializeJavaTimeAsIsoText() throws Exception {
        ObjectMapper mapper = JsonMappers.standard();

        String json = mapper.writeValueAsString(new TimePayload(Instant.parse("2026-05-30T00:00:00Z")));

        assertThat(json).contains("\"at\":\"2026-05-30T00:00:00Z\"");
    }

    @Test
    void standardMapperShouldIgnoreUnknownProperties() throws Exception {
        ObjectMapper mapper = JsonMappers.standard();

        KnownField value = mapper.readValue("{\"name\":\"json\",\"extra\":1}", KnownField.class);

        assertThat(value.name()).isEqualTo("json");
    }

    record TimePayload(Instant at) {
    }

    record KnownField(String name) {
    }
}
