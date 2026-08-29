package com.nowcoder.community.common.json;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.Objects;

public class JacksonJsonCodec {

    private final ObjectMapper objectMapper;

    public JacksonJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Standard codec: module auto-discovery, ISO-8601 dates, unknown properties ignored.
     */
    public static JacksonJsonCodec standard() {
        return new JacksonJsonCodec(standardMapper());
    }

    /**
     * The shared {@link ObjectMapper} configuration used across the backend.
     */
    public static ObjectMapper standardMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("serialize json failed", e);
        }
    }

    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("deserialize json failed", e);
        }
    }

    public JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("deserialize json tree failed", e);
        }
    }

    public <T> T treeToValue(JsonNode node, Class<T> type) {
        try {
            return objectMapper.treeToValue(node, type);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("deserialize json tree failed", e);
        }
    }

    public JsonNode valueToTree(Object value) {
        try {
            return objectMapper.valueToTree(value);
        } catch (IllegalArgumentException e) {
            throw new JsonCodecException("serialize json tree failed", e);
        }
    }
}
