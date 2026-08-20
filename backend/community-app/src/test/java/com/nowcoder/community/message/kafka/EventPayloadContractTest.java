package com.nowcoder.community.message.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.content.contracts.event.PostScorePayload;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 事件 payload 契约测试（consumer 视角）：
 * - payload 必须可被 Jackson 序列化/反序列化（跨服务契约稳定性）
 * - payload 不应携带明显敏感字段（password/token/secret 等）
 */
class EventPayloadContractTest {

    private static final Set<String> BANNED_KEYS = Set.of(
            "password",
            "salt",
            "secret",
            "token",
            "refreshToken",
            "activationCode",
            "email"
    );

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void payloads_shouldBeJacksonSerializable_andNotContainSensitiveFields() throws Exception {
        assertRoundTrip(PostPayload.class, samplePostPayload());
        assertRoundTrip(PostScorePayload.class, new PostScorePayload(uuid(1), 7L, 3L, 12.5));
        assertRoundTrip(CommentPayload.class, sampleCommentPayload());
        assertRoundTrip(ModerationPayload.class, sampleModerationPayload());

        assertRoundTrip(LikePayload.class, sampleLikePayload());
        assertRoundTrip(FollowPayload.class, sampleFollowPayload());
        assertRoundTrip(BlockPayload.class, sampleBlockPayload());
    }

    @Test
    void likePayloadShouldLeaveEventTimeToEnvelope() {
        JsonNode node = objectMapper.valueToTree(sampleLikePayload());

        assertThat(node.has("occurredAt")).isFalse();
        assertThat(node.has("createTime")).isFalse();
    }

    private <T> void assertRoundTrip(Class<T> clazz, T value) throws Exception {
        String json = objectMapper.writeValueAsString(value);
        JsonNode node = objectMapper.readTree(json);
        assertThat(node).as("payload json should not be null: " + clazz.getSimpleName()).isNotNull();

        assertNoBannedKeys(node, clazz.getSimpleName());

        T copy = objectMapper.readValue(json, clazz);
        assertThat(copy).as("payload should be deserializable: " + clazz.getSimpleName()).isNotNull();
    }

    private void assertNoBannedKeys(JsonNode root, String hint) {
        if (root == null) {
            return;
        }
        Deque<JsonNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            JsonNode node = stack.pop();
            if (node == null) {
                continue;
            }
            if (node.isObject()) {
                node.fieldNames().forEachRemaining(field -> {
                    String normalized = field == null ? "" : field.toLowerCase(Locale.ROOT);
                    if (BANNED_KEYS.contains(normalized)) {
                        throw new AssertionError("payload contains banned key '" + field + "': " + hint);
                    }
                    JsonNode child = node.get(field);
                    if (child != null) {
                        stack.push(child);
                    }
                });
                continue;
            }
            if (node.isArray()) {
                for (JsonNode child : node) {
                    if (child != null) {
                        stack.push(child);
                    }
                }
            }
        }
    }

    private PostPayload samplePostPayload() {
        return new PostPayload(
                uuid(1), uuid(2), uuid(3), List.of("tag-a", "tag-b"), "t", "c", 0, 0,
                Instant.now(), null, 0.1D, 0L, 0L);
    }

    private CommentPayload sampleCommentPayload() {
        return new CommentPayload(
                uuid(1), uuid(2), uuid(3), 1, uuid(2), uuid(4), "c", Instant.now(), 2L);
    }

    private ModerationPayload sampleModerationPayload() {
        return new ModerationPayload(
                UUID.fromString("00000000-0000-7000-8000-00000000030b"),
                "report", uuid(2), uuid(3), 1, uuid(10), "mute", "r", 60, Instant.now());
    }

    private LikePayload sampleLikePayload() {
        return new LikePayload(
                uuid(1), 1, uuid(2), uuid(3), uuid(10),
                "like:" + uuid(1) + ":1:" + uuid(2), null, null);
    }

    private FollowPayload sampleFollowPayload() {
        return new FollowPayload(uuid(1), 3, uuid(2), uuid(4), Instant.now());
    }

    private BlockPayload sampleBlockPayload() {
        return new BlockPayload(uuid(1), uuid(2), Boolean.TRUE, null, null);
    }

}
