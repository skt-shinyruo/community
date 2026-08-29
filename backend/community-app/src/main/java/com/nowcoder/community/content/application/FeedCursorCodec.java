package com.nowcoder.community.content.application;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodecException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
public class FeedCursorCodec {

    private static final int HOT_CURSOR_VERSION = 2;
    static final int MAX_CURSOR_PAGE = 200;
    static final int MAX_CURSOR_SIZE = 50;

    private final JacksonJsonCodec jsonCodec;

    public FeedCursorCodec(JacksonJsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public String encodePage(int page, int size) {
        return encode(Map.of(
                "page", Math.max(0, page),
                "size", Math.max(1, size)
        ));
    }

    public String encodeHotPage(int page, int size, HotBoundary boundary) {
        return encodeHotPage(page, size, boundary, 0L);
    }

    public String encodeHotPage(int page, int size, HotBoundary boundary, long projectionEpoch) {
        if (boundary == null
                || !Double.isFinite(boundary.score())
                || boundary.createTime() == null
                || boundary.postId() == null) {
            return encodePage(page, size);
        }
        return encode(Map.of(
                "version", HOT_CURSOR_VERSION,
                "page", Math.max(0, page),
                "size", Math.max(1, size),
                "type", boundary.type(),
                "score", boundary.score(),
                "createTimeMillis", boundary.createTime().getTime(),
                "postId", boundary.postId().toString(),
                "projectionEpoch", Math.max(0L, projectionEpoch)
        ));
    }

    public CursorState decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return CursorState.initial();
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            JsonNode node = jsonCodec.readTree(json);
            if (node == null || !node.isObject()) {
                return CursorState.initial();
            }
            int page = boundedNonNegativeInt(node.get("page"), MAX_CURSOR_PAGE);
            int size = boundedNonNegativeInt(node.get("size"), MAX_CURSOR_SIZE);
            if (page < 0 || size < 0) {
                return CursorState.initial();
            }
            if (node.path("version").asInt(0) != HOT_CURSOR_VERSION) {
                return new CursorState(page, size, null, 0L);
            }
            HotBoundary boundary = decodeHotBoundary(node);
            long projectionEpoch = boundedPositiveLong(node.get("projectionEpoch"));
            return boundary == null
                    ? CursorState.initial()
                    : new CursorState(page, size, boundary, projectionEpoch);
        } catch (IllegalArgumentException | JsonCodecException ex) {
            return CursorState.initial();
        }
    }

    private String encode(Map<String, ?> payload) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(jsonCodec.toJson(payload).getBytes(StandardCharsets.UTF_8));
    }

    private static HotBoundary decodeHotBoundary(JsonNode node) {
        JsonNode typeNode = node.get("type");
        JsonNode scoreNode = node.get("score");
        JsonNode createTimeNode = node.get("createTimeMillis");
        JsonNode postIdNode = node.get("postId");
        if (typeNode == null || !typeNode.isIntegralNumber() || !typeNode.canConvertToInt()
                || scoreNode == null || !scoreNode.isNumber()
                || createTimeNode == null || !createTimeNode.isIntegralNumber() || !createTimeNode.canConvertToLong()
                || postIdNode == null || !postIdNode.isTextual()) {
            return null;
        }
        double score = scoreNode.asDouble();
        long createTimeMillis = createTimeNode.asLong();
        if (!Double.isFinite(score) || createTimeMillis <= 0L) {
            return null;
        }
        return new HotBoundary(
                typeNode.asInt(),
                score,
                new Date(createTimeMillis),
                UUID.fromString(postIdNode.asText())
        );
    }

    private static int boundedNonNegativeInt(JsonNode node, int maximum) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            return 0;
        }
        int value = node.asInt();
        return value >= 0 && value <= maximum ? value : -1;
    }

    private static long boundedPositiveLong(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
            return 0L;
        }
        return Math.max(0L, node.asLong());
    }

    public record CursorState(int page, int size, HotBoundary hotBoundary, long projectionEpoch) {
        public CursorState {
            page = Math.max(0, page);
            size = Math.max(0, size);
            projectionEpoch = Math.max(0L, projectionEpoch);
        }

        public CursorState(int page, int size, HotBoundary hotBoundary) {
            this(page, size, hotBoundary, 0L);
        }

        public static CursorState initial() {
            return new CursorState(0, 0, null, 0L);
        }

        public boolean hasHotBoundary() {
            return hotBoundary != null;
        }
    }

    public record HotBoundary(int type, double score, Date createTime, UUID postId) {
        public HotBoundary {
            createTime = createTime == null ? null : new Date(createTime.getTime());
        }

        @Override
        public Date createTime() {
            return createTime == null ? null : new Date(createTime.getTime());
        }
    }
}
