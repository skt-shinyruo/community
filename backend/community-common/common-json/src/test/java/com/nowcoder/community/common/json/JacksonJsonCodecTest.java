package com.nowcoder.community.common.json;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonJsonCodecTest {

    private final JacksonJsonCodec codec = JacksonJsonCodec.standard();

    @Test
    void shouldSerializeAndDeserializeTypedValues() {
        String json = codec.toJson(new DemoPayload("a"));

        DemoPayload copy = codec.fromJson(json, DemoPayload.class);

        assertThat(copy.value()).isEqualTo("a");
    }

    @Test
    void shouldExposeTreeOperations() {
        JsonNode node = codec.readTree("{\"value\":\"a\"}");

        DemoPayload copy = codec.treeToValue(node, DemoPayload.class);
        JsonNode tree = codec.valueToTree(copy);

        assertThat(copy.value()).isEqualTo("a");
        assertThat(tree.path("value").asText()).isEqualTo("a");
    }

    @Test
    void shouldWrapSerializationFailures() {
        assertThatThrownBy(() -> codec.toJson(new ExplodingBean()))
                .isInstanceOf(JsonCodecException.class)
                .hasMessageContaining("serialize");
    }

    @Test
    void shouldNotWrapJvmErrors() {
        assertThatThrownBy(() -> codec.toJson(new FatalBean()))
                .isInstanceOf(StackOverflowError.class);
    }

    @Test
    void shouldWrapDeserializationFailures() {
        assertThatThrownBy(() -> codec.fromJson("{", DemoPayload.class))
                .isInstanceOf(JsonCodecException.class)
                .hasMessageContaining("deserialize");
    }

    record DemoPayload(String value) {
    }

    @Test
    void standardCodecShouldSerializeJavaTimeAsIsoText() {
        String json = codec.toJson(new TimePayload(java.time.Instant.parse("2026-05-30T00:00:00Z")));

        assertThat(json).contains("\"at\":\"2026-05-30T00:00:00Z\"");
    }

    @Test
    void standardCodecShouldIgnoreUnknownProperties() {
        KnownField value = codec.fromJson("{\"name\":\"json\",\"extra\":1}", KnownField.class);

        assertThat(value.name()).isEqualTo("json");
    }

    record TimePayload(java.time.Instant at) {
    }

    record KnownField(String name) {
    }

    static class ExplodingBean {

        public String getValue() {
            throw new IllegalStateException("boom");
        }
    }

    static class FatalBean {

        public String getValue() {
            throw new StackOverflowError("fatal");
        }
    }
}
