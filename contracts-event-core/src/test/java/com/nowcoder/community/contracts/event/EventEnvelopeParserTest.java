package com.nowcoder.community.contracts.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEnvelopeParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void parse_shouldParseRequiredFields() throws Exception {
        EventEnvelope<Object> env = EventEnvelope.of("demo.type", 1, "demo-producer", Map.of("k", "v"));
        env.setEventId("evt_1");
        env.setTraceId("trace_1");

        String json = objectMapper.writeValueAsString(env);
        EventEnvelopeParser.ParsedEnvelope parsed = EventEnvelopeParser.parse(objectMapper, json);

        assertThat(parsed.getEventId()).isEqualTo("evt_1");
        assertThat(parsed.getType()).isEqualTo("demo.type");
        assertThat(parsed.getVersion()).isEqualTo(1);
        assertThat(parsed.getPayload()).isNotNull();
    }

    @Test
    void parse_shouldFailClosedWhenPayloadMissing() {
        String json = """
                {
                  "eventId": "evt_1",
                  "type": "demo.type",
                  "version": 1
                }
                """;
        assertThatThrownBy(() -> EventEnvelopeParser.parse(objectMapper, json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
    }

    @Test
    void parse_shouldRejectInvalidJson() {
        assertThatThrownBy(() -> EventEnvelopeParser.parse(objectMapper, "{"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON");
    }
}

