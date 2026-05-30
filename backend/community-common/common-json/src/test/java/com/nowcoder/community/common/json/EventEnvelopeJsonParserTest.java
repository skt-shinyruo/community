package com.nowcoder.community.common.json;

import com.nowcoder.community.common.event.EventEnvelope;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEnvelopeJsonParserTest {

    private final JsonCodec codec = new JacksonJsonCodec(JsonMappers.standard());

    @Test
    void shouldParseEnvelopeAndExposePayloadTree() {
        EventEnvelope<Object> env = EventEnvelope.of("demo.type", 1, "demo-producer", Map.of("k", "v"), "trace-1");
        String json = codec.toJson(env);

        EventEnvelopeJsonParser.ParsedEnvelope parsed = EventEnvelopeJsonParser.parse(codec, json);

        assertThat(parsed.getEventId()).isNotBlank();
        assertThat(parsed.getType()).isEqualTo("demo.type");
        assertThat(parsed.getVersion()).isEqualTo(1);
        assertThat(parsed.getTraceId()).isEqualTo("trace-1");
        assertThat(parsed.getProducer()).isEqualTo("demo-producer");
        assertThat(parsed.getOccurredAt()).isNotNull();
        assertThat(parsed.getPayload().path("k").asText()).isEqualTo("v");
    }

    @Test
    void shouldRejectMissingRequiredFields() {
        assertThatThrownBy(() -> EventEnvelopeJsonParser.parse(codec, "{\"type\":\"demo.type\",\"version\":1,\"payload\":{}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void shouldRejectInvalidJson() {
        assertThatThrownBy(() -> EventEnvelopeJsonParser.parse(codec, "{"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法 JSON");
    }
}
