package com.nowcoder.community.content.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class CommentCursorCodec {

    private static final int VERSION = 1;
    private static final String INVALID_CURSOR_MESSAGE = "评论游标非法";
    private static final Instant MYSQL_TIMESTAMP_MIN = Instant.parse("1970-01-01T00:00:01Z");
    private static final Instant MYSQL_TIMESTAMP_MAX = Instant.parse("2038-01-19T03:14:07Z");
    private static final Set<String> PAYLOAD_FIELDS = Set.of(
            "version",
            "kind",
            "postId",
            "rootCommentId",
            "createTime",
            "commentId"
    );

    private final JsonCodec jsonCodec;

    public CommentCursorCodec(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public Optional<Boundary> decodeRoot(String cursor, UUID postId) {
        return decode(cursor, Kind.ROOT, postId, null);
    }

    public Optional<Boundary> decodeReply(String cursor, UUID postId, UUID rootCommentId) {
        return decode(cursor, Kind.REPLY, postId, rootCommentId);
    }

    public String encodeRoot(UUID postId, Instant createTime, UUID commentId) {
        return encode(Kind.ROOT, postId, null, createTime, commentId);
    }

    public String encodeReply(UUID postId, UUID rootCommentId, Instant createTime, UUID commentId) {
        return encode(Kind.REPLY, postId, rootCommentId, createTime, commentId);
    }

    private Optional<Boundary> decode(
            String cursor,
            Kind expectedKind,
            UUID expectedPostId,
            UUID expectedRootCommentId
    ) {
        if (!StringUtils.hasText(cursor)) {
            return Optional.empty();
        }
        try {
            if (expectedPostId == null
                    || (expectedKind == Kind.REPLY && expectedRootCommentId == null)) {
                throw invalidCursor();
            }
            String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            JsonNode node = jsonCodec.readTree(json);
            validatePayloadShape(node);

            JsonNode versionNode = node.get("version");
            if (!versionNode.isIntegralNumber()
                    || !versionNode.canConvertToInt()
                    || versionNode.asInt() != VERSION) {
                throw invalidCursor();
            }

            Kind kind = Kind.valueOf(requiredText(node, "kind"));
            UUID postId = parseUuid(requiredText(node, "postId"));
            UUID rootCommentId = parseRootCommentId(node.get("rootCommentId"), kind);
            Instant createTime = parseCreateTime(requiredText(node, "createTime"));
            UUID commentId = parseUuid(requiredText(node, "commentId"));

            if (kind != expectedKind
                    || !postId.equals(expectedPostId)
                    || !Objects.equals(rootCommentId, expectedRootCommentId)) {
                throw invalidCursor();
            }
            return Optional.of(new Boundary(createTime, commentId));
        } catch (BusinessException error) {
            throw error;
        } catch (IllegalArgumentException | DateTimeException | JsonCodecException ignored) {
            throw invalidCursor();
        }
    }

    private String encode(
            Kind kind,
            UUID postId,
            UUID rootCommentId,
            Instant createTime,
            UUID commentId
    ) {
        if (kind == null
                || postId == null
                || createTime == null
                || commentId == null
                || (kind == Kind.ROOT && rootCommentId != null)
                || (kind == Kind.REPLY && rootCommentId == null)) {
            throw invalidCursor();
        }
        Payload payload = new Payload(
                VERSION,
                kind.name(),
                postId.toString(),
                rootCommentId == null ? null : rootCommentId.toString(),
                createTime.toString(),
                commentId.toString()
        );
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(jsonCodec.toJson(payload).getBytes(StandardCharsets.UTF_8));
    }

    private void validatePayloadShape(JsonNode node) {
        if (node == null || !node.isObject() || node.size() != PAYLOAD_FIELDS.size()) {
            throw invalidCursor();
        }
        for (String field : PAYLOAD_FIELDS) {
            if (!node.has(field)) {
                throw invalidCursor();
            }
        }
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.textValue())) {
            throw invalidCursor();
        }
        return value.textValue();
    }

    private UUID parseRootCommentId(JsonNode node, Kind kind) {
        if (kind == Kind.ROOT) {
            if (node == null || !node.isNull()) {
                throw invalidCursor();
            }
            return null;
        }
        if (node == null || !node.isTextual() || !StringUtils.hasText(node.textValue())) {
            throw invalidCursor();
        }
        return parseUuid(node.textValue());
    }

    private UUID parseUuid(String value) {
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equalsIgnoreCase(value)) {
            throw invalidCursor();
        }
        return parsed;
    }

    private Instant parseCreateTime(String value) {
        Instant createTime = Instant.parse(value);
        Date.from(createTime);
        if (createTime.isBefore(MYSQL_TIMESTAMP_MIN) || createTime.isAfter(MYSQL_TIMESTAMP_MAX)) {
            throw invalidCursor();
        }
        return createTime;
    }

    private BusinessException invalidCursor() {
        return new BusinessException(CommonErrorCode.INVALID_ARGUMENT, INVALID_CURSOR_MESSAGE);
    }

    public enum Kind {
        ROOT,
        REPLY
    }

    public record Boundary(Instant createTime, UUID commentId) {
        public Boundary {
            Objects.requireNonNull(createTime, "createTime must not be null");
            Objects.requireNonNull(commentId, "commentId must not be null");
        }
    }

    private record Payload(
            int version,
            String kind,
            String postId,
            String rootCommentId,
            String createTime,
            String commentId
    ) {
    }
}
