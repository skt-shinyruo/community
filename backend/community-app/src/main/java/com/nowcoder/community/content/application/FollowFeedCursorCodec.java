package com.nowcoder.community.content.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@Component
public class FollowFeedCursorCodec {

    private final JsonCodec jsonCodec;

    public FollowFeedCursorCodec(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public String encode(int size, long anchorCreateTimeMillis, UUID anchorPostId) {
        if (anchorPostId == null) {
            return "";
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(jsonCodec.toJson(java.util.Map.of(
                        "size", Math.max(1, size),
                        "anchorCreateTimeMillis", anchorCreateTimeMillis,
                        "anchorPostId", anchorPostId.toString()
                )).getBytes(StandardCharsets.UTF_8));
    }

    public CursorState decode(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return CursorState.initial();
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            JsonNode node = jsonCodec.readTree(json);
            if (node == null || !node.isObject()) {
                return CursorState.initial();
            }
            JsonNode sizeNode = node.get("size");
            JsonNode timeNode = node.get("anchorCreateTimeMillis");
            JsonNode postIdNode = node.get("anchorPostId");
            if (sizeNode == null || !sizeNode.canConvertToInt()) {
                return CursorState.initial();
            }
            if (timeNode == null || !timeNode.canConvertToLong()) {
                return CursorState.initial();
            }
            if (postIdNode == null || !postIdNode.isTextual()) {
                return CursorState.initial();
            }
            UUID anchorPostId = UUID.fromString(postIdNode.asText());
            return new CursorState(cursor, Math.max(1, sizeNode.asInt()), timeNode.asLong(), anchorPostId);
        } catch (IllegalArgumentException | JsonCodecException ex) {
            return CursorState.initial();
        }
    }

    public record CursorState(String normalizedCursor, int size, long anchorCreateTimeMillis, UUID anchorPostId) {

        public CursorState {
            normalizedCursor = StringUtils.hasText(normalizedCursor) ? normalizedCursor : "";
            size = Math.max(0, size);
        }

        public static CursorState initial() {
            return new CursorState("", 0, 0L, null);
        }

        public boolean hasAnchor() {
            return anchorPostId != null && anchorCreateTimeMillis > 0L;
        }
    }
}
