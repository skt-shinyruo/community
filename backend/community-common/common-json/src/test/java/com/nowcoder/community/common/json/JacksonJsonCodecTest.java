package com.nowcoder.community.common.json;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonJsonCodecTest {

    private final JsonCodec codec = new JacksonJsonCodec(JsonMappers.standard());

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
