package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentCursorCodecTest {

    private static final String INVALID_CURSOR_MESSAGE = "评论游标非法";

    private final JacksonJsonCodec jsonCodec = new JacksonJsonCodec(JacksonJsonCodec.standardMapper());
    private final CommentCursorCodec codec = new CommentCursorCodec(jsonCodec);

    @Test
    void blankCursorShouldHaveNoBoundary() {
        UUID postId = uuid(1);
        UUID rootCommentId = uuid(2);

        assertThat(codec.decodeRoot(null, postId, CommentSort.LATEST)).isEqualTo(Optional.empty());
        assertThat(codec.decodeRoot("  ", postId, CommentSort.HOT)).isEqualTo(Optional.empty());
        assertThat(codec.decodeReply("", postId, rootCommentId)).isEqualTo(Optional.empty());
    }

    @Test
    void rootCursorShouldRoundTripExactBoundary() {
        UUID postId = uuid(10);
        UUID commentId = uuid(11);
        Instant createTime = Instant.parse("2026-07-21T01:02:03.123456789Z");

        String cursor = codec.encodeRoot(postId, CommentSort.LATEST, 0L, createTime, commentId);

        assertThat(codec.decodeRoot(cursor, postId, CommentSort.LATEST))
                .contains(new CommentCursorCodec.Boundary(0L, createTime, commentId));
    }

    @Test
    void replyCursorShouldRoundTripExactBoundary() {
        UUID postId = uuid(20);
        UUID rootCommentId = uuid(21);
        UUID commentId = uuid(22);
        Instant createTime = Instant.parse("2026-07-21T02:03:04.987654321Z");

        String cursor = codec.encodeReply(postId, rootCommentId, createTime, commentId);

        assertThat(codec.decodeReply(cursor, postId, rootCommentId))
                .contains(new CommentCursorCodec.Boundary(createTime, commentId));
    }

    @Test
    void hotRootCursorShouldRoundTripSortLikeCountAndBoundary() {
        UUID postId = uuid(70);
        UUID commentId = uuid(71);
        Instant createTime = Instant.parse("2026-07-21T07:08:09Z");

        String cursor = codec.encodeRoot(postId, CommentSort.HOT, 42L, createTime, commentId);

        assertThat(codec.decodeRoot(cursor, postId, CommentSort.HOT))
                .contains(new CommentCursorCodec.Boundary(42L, createTime, commentId));
    }

    @Test
    void timeRootCursorShouldCarryLikeCountZero() {
        UUID postId = uuid(72);
        UUID commentId = uuid(73);

        String cursor = codec.encodeRoot(postId, CommentSort.EARLIEST, 0L, Instant.EPOCH.plusSeconds(60), commentId);

        assertThat(codec.decodeRoot(cursor, postId, CommentSort.EARLIEST))
                .contains(new CommentCursorCodec.Boundary(0L, Instant.EPOCH.plusSeconds(60), commentId));
    }

    @Test
    void rootCursorSortMustMatchRequestScope() {
        UUID postId = uuid(74);
        UUID commentId = uuid(75);
        Instant createTime = Instant.parse("2026-07-21T08:09:10Z");
        String latestCursor = codec.encodeRoot(postId, CommentSort.LATEST, 0L, createTime, commentId);
        String hotCursor = codec.encodeRoot(postId, CommentSort.HOT, 3L, createTime, commentId);

        assertInvalid(hotCursor, () -> codec.decodeRoot(hotCursor, postId, CommentSort.LATEST));
        assertInvalid(latestCursor, () -> codec.decodeRoot(latestCursor, postId, CommentSort.HOT));
    }

    @Test
    void negativeLikeCountCursorShouldReturnStableInvalidArgument() {
        UUID postId = uuid(76);
        UUID commentId = uuid(77);
        String cursor = encodePayload(payload(
                2,
                "ROOT",
                postId.toString(),
                null,
                "HOT",
                -1L,
                "2026-07-21T09:10:11Z",
                commentId.toString()
        ));

        assertInvalid(cursor, () -> codec.decodeRoot(cursor, postId, CommentSort.HOT));
    }

    @Test
    void parseableButDateUnrepresentableCursorTimesShouldReturnStableInvalidArgument() {
        UUID postId = uuid(23);
        UUID commentId = uuid(24);

        for (Instant createTime : List.of(Instant.MIN, Instant.MAX)) {
            String cursor = encodePayload(payload(
                    2,
                    "ROOT",
                    postId.toString(),
                    null,
                    "HOT",
                    0L,
                    createTime.toString(),
                    commentId.toString()
            ));

            assertInvalid(cursor, () -> codec.decodeRoot(cursor, postId, CommentSort.HOT));
        }
    }

    @Test
    void dateRepresentableButMysqlTimestampOutOfRangeCursorTimesShouldReturnStableInvalidArgument() {
        UUID postId = uuid(25);
        UUID commentId = uuid(26);
        List<Instant> createTimes = List.of(
                Instant.parse("1970-01-01T00:00:00Z"),
                Instant.parse("2038-01-19T03:14:08Z")
        );

        for (Instant createTime : createTimes) {
            String cursor = encodePayload(payload(
                    2,
                    "ROOT",
                    postId.toString(),
                    null,
                    "HOT",
                    0L,
                    createTime.toString(),
                    commentId.toString()
            ));

            assertInvalid(cursor, () -> codec.decodeRoot(cursor, postId, CommentSort.HOT));
        }
    }

    @Test
    void malformedCursorShouldReturnStableInvalidArgument() {
        UUID postId = uuid(30);
        List<String> cursors = List.of(
                "%%%",
                encodeJson("{not-json"),
                encodePayload(payload(3, "ROOT", postId.toString(), null,
                        "HOT", 0L, "2026-07-21T03:04:05Z", uuid(31).toString()))
        );

        for (String cursor : cursors) {
            assertInvalid(cursor, () -> codec.decodeRoot(cursor, postId, CommentSort.HOT));
        }
    }

    @Test
    void missingOrInvalidPayloadFieldsShouldReturnStableInvalidArgument() {
        UUID postId = uuid(40);
        UUID commentId = uuid(41);
        List<Map<String, Object>> payloads = List.of(
                Map.of(),
                payload(2, "ROOT", postId.toString(), null,
                        "HOT", 0L, "2026-07-21T04:05:06Z", null),
                payload(2, "ROOT", "not-a-uuid", null,
                        "HOT", 0L, "2026-07-21T04:05:06Z", commentId.toString()),
                payload(2, "ROOT", postId.toString(), null,
                        "not-a-sort", 0L, "2026-07-21T04:05:06Z", commentId.toString()),
                payload(2, "ROOT", postId.toString(), null,
                        "HOT", "7", "2026-07-21T04:05:06Z", commentId.toString()),
                payload(2, "ROOT", postId.toString(), null,
                        "HOT", 0L, "not-an-instant", commentId.toString()),
                payload(2, "ROOT", postId.toString(), null,
                        "HOT", 0L, "2026-07-21T04:05:06Z", "not-a-uuid"),
                payload("2", "ROOT", postId.toString(), null,
                        "HOT", 0L, "2026-07-21T04:05:06Z", commentId.toString()),
                payload(2, 7, postId.toString(), null,
                        "HOT", 0L, "2026-07-21T04:05:06Z", commentId.toString())
        );

        for (Map<String, Object> payload : payloads) {
            String cursor = encodePayload(payload);
            assertInvalid(cursor, () -> codec.decodeRoot(cursor, postId, CommentSort.HOT));
        }
    }

    @Test
    void cursorKindAndScopeMustMatchRequest() {
        UUID postId = uuid(50);
        UUID otherPostId = uuid(51);
        UUID rootCommentId = uuid(52);
        UUID otherRootCommentId = uuid(53);
        Instant createTime = Instant.parse("2026-07-21T05:06:07Z");
        UUID rootBoundaryId = uuid(54);
        UUID replyBoundaryId = uuid(55);
        String rootCursor = codec.encodeRoot(postId, CommentSort.LATEST, 0L, createTime, rootBoundaryId);
        String replyCursor = codec.encodeReply(postId, rootCommentId, createTime, replyBoundaryId);

        assertInvalid(rootCursor, () -> codec.decodeReply(rootCursor, postId, rootCommentId));
        assertInvalid(replyCursor, () -> codec.decodeRoot(replyCursor, postId, CommentSort.LATEST));
        assertInvalid(rootCursor, () -> codec.decodeRoot(rootCursor, otherPostId, CommentSort.LATEST));
        assertInvalid(replyCursor, () -> codec.decodeReply(replyCursor, otherPostId, rootCommentId));
        assertInvalid(replyCursor, () -> codec.decodeReply(replyCursor, postId, otherRootCommentId));
    }

    @Test
    void replyCursorMustContainRootScopeAndRootCursorMustNot() {
        UUID postId = uuid(60);
        UUID commentId = uuid(61);
        String replyWithoutRoot = encodePayload(payload(
                2,
                "REPLY",
                postId.toString(),
                null,
                null,
                0L,
                "2026-07-21T06:07:08Z",
                commentId.toString()
        ));
        String rootWithRoot = encodePayload(payload(
                2,
                "ROOT",
                postId.toString(),
                uuid(62).toString(),
                "HOT",
                0L,
                "2026-07-21T06:07:08Z",
                commentId.toString()
        ));

        assertInvalid(replyWithoutRoot, () -> codec.decodeReply(replyWithoutRoot, postId, uuid(62)));
        assertInvalid(rootWithRoot, () -> codec.decodeRoot(rootWithRoot, postId, CommentSort.HOT));
    }

    private Map<String, Object> payload(
            Object version,
            Object kind,
            Object postId,
            Object rootCommentId,
            Object sort,
            Object likeCount,
            Object createTime,
            Object commentId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", version);
        payload.put("kind", kind);
        payload.put("postId", postId);
        payload.put("rootCommentId", rootCommentId);
        payload.put("sort", sort);
        payload.put("likeCount", likeCount);
        payload.put("createTime", createTime);
        payload.put("commentId", commentId);
        return payload;
    }

    private String encodePayload(Map<String, Object> payload) {
        return encodeJson(jsonCodec.toJson(payload));
    }

    private String encodeJson(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private void assertInvalid(String cursor, Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT);
                    assertThat(error.getMessage()).isEqualTo(INVALID_CURSOR_MESSAGE);
                    assertThat(error.getMessage()).doesNotContain(cursor);
                });
    }
}
