package com.nowcoder.community.common.json;

import com.fasterxml.jackson.databind.JsonNode;

public interface JsonCodec {

    String toJson(Object value);

    <T> T fromJson(String json, Class<T> type);

    JsonNode readTree(String json);

    <T> T treeToValue(JsonNode node, Class<T> type);

    JsonNode valueToTree(Object value);
}
