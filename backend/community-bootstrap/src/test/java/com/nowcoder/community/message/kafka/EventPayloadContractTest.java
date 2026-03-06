package com.nowcoder.community.message.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.api.event.payload.CommentPayload;
import com.nowcoder.community.content.api.event.payload.ModerationCommandPayload;
import com.nowcoder.community.content.api.event.payload.ModerationPayload;
import com.nowcoder.community.content.api.event.payload.PostPayload;
import com.nowcoder.community.social.api.event.payload.BlockPayload;
import com.nowcoder.community.social.api.event.payload.FollowPayload;
import com.nowcoder.community.social.api.event.payload.LikePayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        assertRoundTrip(CommentPayload.class, sampleCommentPayload());
        assertRoundTrip(ModerationPayload.class, sampleModerationPayload());
        assertRoundTrip(ModerationCommandPayload.class, sampleModerationCommandPayload());

        assertRoundTrip(LikePayload.class, sampleLikePayload());
        assertRoundTrip(FollowPayload.class, sampleFollowPayload());
        assertRoundTrip(BlockPayload.class, sampleBlockPayload());
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
        PostPayload p = new PostPayload();
        p.setPostId(1);
        p.setUserId(2);
        p.setCategoryId(3);
        p.setTags(List.of("tag-a", "tag-b"));
        p.setTitle("t");
        p.setContent("c");
        p.setType(0);
        p.setStatus(0);
        p.setCreateTime(Instant.now());
        p.setScore(0.1D);
        return p;
    }

    private CommentPayload sampleCommentPayload() {
        CommentPayload p = new CommentPayload();
        p.setCommentId(1);
        p.setPostId(2);
        p.setUserId(3);
        p.setEntityType(1);
        p.setEntityId(2);
        p.setTargetUserId(4);
        p.setContent("c");
        p.setCreateTime(Instant.now());
        return p;
    }

    private ModerationPayload sampleModerationPayload() {
        ModerationPayload p = new ModerationPayload();
        p.setReportId(1);
        p.setKind("report");
        p.setToUserId(2);
        p.setActorUserId(3);
        p.setTargetType(1);
        p.setTargetId(10);
        p.setAction("mute");
        p.setReason("r");
        p.setDurationSeconds(60);
        p.setCreateTime(Instant.now());
        return p;
    }

    private ModerationCommandPayload sampleModerationCommandPayload() {
        ModerationCommandPayload p = new ModerationCommandPayload();
        p.setUserId(1);
        p.setAction("ban");
        p.setDurationSeconds(3600);
        p.setActorUserId(2);
        p.setReportId(3);
        p.setReason("r");
        return p;
    }

    private LikePayload sampleLikePayload() {
        LikePayload p = new LikePayload();
        p.setActorUserId(1);
        p.setEntityType(1);
        p.setEntityId(2);
        p.setEntityUserId(3);
        p.setPostId(10);
        p.setCreateTime(Instant.now());
        return p;
    }

    private FollowPayload sampleFollowPayload() {
        FollowPayload p = new FollowPayload();
        p.setActorUserId(1);
        p.setEntityType(3);
        p.setEntityId(2);
        p.setEntityUserId(4);
        p.setCreateTime(Instant.now());
        return p;
    }

    private BlockPayload sampleBlockPayload() {
        BlockPayload p = new BlockPayload();
        p.setBlockerUserId(1);
        p.setBlockedUserId(2);
        p.setBlocked(Boolean.TRUE);
        return p;
    }

}
