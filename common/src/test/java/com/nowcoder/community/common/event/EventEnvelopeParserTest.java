package com.nowcoder.community.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEnvelopeParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldParseUnknownTypeAndUnsupportedVersionSoConsumerCanDecideSkipOrDlq() throws Exception {
        EventEnvelope<Object> env = EventEnvelope.of("UnknownType", 2, "test-producer", java.util.Map.of("k", "v"));
        String json = objectMapper.writeValueAsString(env);

        EventEnvelopeParser.ParsedEnvelope parsed = EventEnvelopeParser.parse(objectMapper, json);
        assertThat(parsed.getType()).isEqualTo("UnknownType");
        assertThat(parsed.getVersion()).isEqualTo(2);
        assertThat(parsed.getEventId()).isNotBlank();
        assertThat(parsed.getPayload()).isNotNull();
    }

    @Test
    void shouldRejectWhenEventIdMissing() {
        String json = "{\"type\":\"T\",\"version\":1,\"payload\":{}}";
        assertThatThrownBy(() -> EventEnvelopeParser.parse(objectMapper, json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void shouldRejectWhenTypeMissing() {
        String json = "{\"eventId\":\"e1\",\"version\":1,\"payload\":{}}";
        assertThatThrownBy(() -> EventEnvelopeParser.parse(objectMapper, json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    @Test
    void shouldRejectWhenVersionMissingOrInvalid() {
        String json = "{\"eventId\":\"e1\",\"type\":\"T\",\"version\":0,\"payload\":{}}";
        assertThatThrownBy(() -> EventEnvelopeParser.parse(objectMapper, json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    @Test
    void shouldRejectWhenPayloadMissing() {
        String json = "{\"eventId\":\"e1\",\"type\":\"T\",\"version\":1}";
        assertThatThrownBy(() -> EventEnvelopeParser.parse(objectMapper, json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
    }
}

