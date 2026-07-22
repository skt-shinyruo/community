package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.application.result.CommentPageResult;
import com.nowcoder.community.content.application.result.CommentResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.infrastructure.text.SpringHtmlContentTextCodec;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;
import static com.nowcoder.community.content.support.CommentTestBuilder.aComment;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommentReadApplicationServiceTest {

    @Test
    void listRootCommentsShouldUseInitialKeysetAndProbeBoundaryForNextCursor() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        CommentCursorCodec cursorCodec = cursorCodec();
        CommentReadApplicationService service = service(
                commentContentRepository, postContentRepository, commentPageCache, cursorCodec);
        UUID postId = uuid(100);
        UUID firstCommentId = uuid(200);
        UUID secondCommentId = uuid(201);
        UUID probeCommentId = uuid(202);
        Instant firstTime = Instant.parse("2026-07-06T13:00:03Z");
        Instant secondTime = Instant.parse("2026-07-06T13:00:02Z");
        Comment firstComment = comment(
                firstCommentId, uuid(7), postId, firstCommentId, null, null, "root &amp; comment", firstTime);
        Comment secondComment = comment(
                secondCommentId, uuid(8), postId, secondCommentId, null, null, "second", secondTime);
        Comment probeComment = comment(
                probeCommentId, uuid(9), postId, probeCommentId, null, null, "probe",
                Instant.parse("2026-07-06T13:00:01Z"));
        when(commentContentRepository.listRootCommentsAfter(postId, null, null, 3))
                .thenReturn(List.of(firstComment, secondComment, probeComment));

        CommentPageResult page = service.listRootComments(postId, "", 2);

        assertThat(page.items()).extracting(CommentResult::id)
                .containsExactly(firstCommentId, secondCommentId);
        assertThat(page.items().get(0)).satisfies(item -> {
            assertThat(item.postId()).isEqualTo(postId);
            assertThat(item.rootCommentId()).isEqualTo(firstCommentId);
            assertThat(item.parentCommentId()).isNull();
            assertThat(item.replyToUserId()).isNull();
            assertThat(item.content()).isEqualTo("root & comment");
        });
        assertThat(cursorCodec.decodeRoot(page.nextCursor(), postId))
                .contains(new CommentCursorCodec.Boundary(secondTime, secondCommentId));
        verify(postContentRepository).getById(postId);
        verify(commentContentRepository).listRootCommentsAfter(postId, null, null, 3);
        verify(commentPageCache).putRootPage(postId, "", 2, page);
    }

    @Test
    void listRootCommentsShouldNotReturnNextCursorWithoutProbeRow() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        CommentReadApplicationService service = service(
                commentContentRepository, postContentRepository, commentPageCache, cursorCodec());
        UUID postId = uuid(100);
        UUID firstCommentId = uuid(200);
        UUID secondCommentId = uuid(201);
        Comment firstComment = comment(
                firstCommentId, uuid(7), postId, firstCommentId, null, null, "first",
                Instant.parse("2026-07-06T13:00:02Z"));
        Comment secondComment = comment(
                secondCommentId, uuid(8), postId, secondCommentId, null, null, "second",
                Instant.parse("2026-07-06T13:00:01Z"));
        when(commentContentRepository.listRootCommentsAfter(postId, null, null, 3))
                .thenReturn(List.of(firstComment, secondComment));

        CommentPageResult page = service.listRootComments(postId, "", 2);

        assertThat(page.items()).extracting(CommentResult::id)
                .containsExactly(firstCommentId, secondCommentId);
        assertThat(page.nextCursor()).isBlank();
        verify(postContentRepository).getById(postId);
        verify(commentContentRepository).listRootCommentsAfter(postId, null, null, 3);
        verify(commentPageCache).putRootPage(postId, "", 2, page);
    }

    @Test
    void listRootCommentsShouldServeFirstPageFromCache() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        CommentReadApplicationService service = service(
                commentContentRepository, postContentRepository, commentPageCache, cursorCodec());
        UUID postId = uuid(100);
        CommentPageResult cached = new CommentPageResult(List.of(commentResult(postId)), "next");
        when(commentPageCache.getRootPage(postId, "", 10)).thenReturn(cached);

        CommentPageResult result = service.listRootComments(postId, "", 10);

        assertThat(result).isSameAs(cached);
        verify(postContentRepository).getById(postId);
        verifyNoInteractions(commentContentRepository);
    }

    @Test
    void listRootCommentsShouldValidatePostBeforeServingFirstPageFromCache() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        CommentReadApplicationService service = service(
                commentContentRepository, postContentRepository, commentPageCache, cursorCodec());
        UUID postId = uuid(100);
        when(postContentRepository.getById(postId)).thenThrow(new BusinessException(NOT_FOUND));

        assertThatThrownBy(() -> service.listRootComments(postId, "", 10))
                .isInstanceOf(BusinessException.class);

        verify(postContentRepository).getById(postId);
        verifyNoInteractions(commentPageCache, commentContentRepository);
    }

    @Test
    void listRootCommentsShouldDecodeExactBoundaryAndSkipFirstPageCache() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        CommentCursorCodec cursorCodec = cursorCodec();
        CommentReadApplicationService service = service(
                commentContentRepository, postContentRepository, commentPageCache, cursorCodec);
        UUID postId = uuid(100);
        UUID boundaryId = uuid(210);
        Instant boundaryTime = Instant.parse("2026-07-06T13:00:02.123Z");
        String cursor = cursorCodec.encodeRoot(postId, boundaryTime, boundaryId);

        service.listRootComments(postId, cursor, 10);

        verify(postContentRepository).getById(postId);
        verify(commentContentRepository).listRootCommentsAfter(
                postId, Date.from(boundaryTime), boundaryId, 11);
        verifyNoInteractions(commentPageCache);
    }

    @Test
    void invalidOrCrossPostRootCursorShouldFailBeforeRepositoryCalls() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        CommentCursorCodec cursorCodec = cursorCodec();
        CommentReadApplicationService service = service(
                commentContentRepository, postContentRepository, commentPageCache, cursorCodec);
        UUID postId = uuid(100);
        String crossPostCursor = cursorCodec.encodeRoot(
                uuid(101), Instant.parse("2026-07-06T13:00:00Z"), uuid(201));

        assertInvalidCursor(() -> service.listRootComments(postId, "%%%", 10));
        assertInvalidCursor(() -> service.listRootComments(postId, crossPostCursor, 10));

        verifyNoInteractions(commentContentRepository, postContentRepository, commentPageCache);
    }

    @Test
    void listRepliesShouldUseInitialKeysetAndProbeBoundaryForNextCursor() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        CommentCursorCodec cursorCodec = cursorCodec();
        CommentReadApplicationService service = service(
                commentContentRepository, mock(PostContentRepository.class), mock(CommentPageCache.class), cursorCodec);
        UUID postId = uuid(100);
        UUID rootCommentId = uuid(200);
        UUID replyId = uuid(201);
        UUID parentCommentId = uuid(202);
        UUID replyToUserId = uuid(9);
        Instant replyTime = Instant.parse("2026-07-06T13:00:01Z");
        Comment replyComment = comment(
                replyId, uuid(8), postId, rootCommentId, parentCommentId, replyToUserId, "reply", replyTime);
        Comment probeComment = comment(
                uuid(203), uuid(10), postId, rootCommentId, replyId, uuid(8), "probe",
                Instant.parse("2026-07-06T13:00:02Z"));
        when(commentContentRepository.listRepliesAfter(rootCommentId, null, null, 2))
                .thenReturn(List.of(replyComment, probeComment));

        CommentPageResult page = service.listReplies(postId, rootCommentId, "", 1);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(replyId);
            assertThat(item.postId()).isEqualTo(postId);
            assertThat(item.rootCommentId()).isEqualTo(rootCommentId);
            assertThat(item.parentCommentId()).isEqualTo(parentCommentId);
            assertThat(item.replyToUserId()).isEqualTo(replyToUserId);
        });
        assertThat(cursorCodec.decodeReply(page.nextCursor(), postId, rootCommentId))
                .contains(new CommentCursorCodec.Boundary(replyTime, replyId));
        verify(commentContentRepository).assertCommentBelongsToPost(postId, rootCommentId);
        verify(commentContentRepository).listRepliesAfter(rootCommentId, null, null, 2);
    }

    @Test
    void listRepliesShouldDecodeExactBoundary() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        CommentCursorCodec cursorCodec = cursorCodec();
        CommentReadApplicationService service = service(
                commentContentRepository, mock(PostContentRepository.class), mock(CommentPageCache.class), cursorCodec);
        UUID postId = uuid(100);
        UUID rootCommentId = uuid(200);
        UUID boundaryId = uuid(201);
        Instant boundaryTime = Instant.parse("2026-07-06T13:00:01.123Z");
        String cursor = cursorCodec.encodeReply(postId, rootCommentId, boundaryTime, boundaryId);

        service.listReplies(postId, rootCommentId, cursor, 10);

        verify(commentContentRepository).assertCommentBelongsToPost(postId, rootCommentId);
        verify(commentContentRepository).listRepliesAfter(
                rootCommentId, Date.from(boundaryTime), boundaryId, 11);
    }

    @Test
    void crossRootOrWrongKindReplyCursorShouldFailBeforeRepositoryCalls() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        CommentCursorCodec cursorCodec = cursorCodec();
        CommentReadApplicationService service = service(
                commentContentRepository, postContentRepository, commentPageCache, cursorCodec);
        UUID postId = uuid(100);
        UUID rootCommentId = uuid(200);
        Instant boundaryTime = Instant.parse("2026-07-06T13:00:01Z");
        String crossRootCursor = cursorCodec.encodeReply(postId, uuid(201), boundaryTime, uuid(202));
        String rootCursor = cursorCodec.encodeRoot(postId, boundaryTime, uuid(203));

        assertInvalidCursor(() -> service.listReplies(postId, rootCommentId, crossRootCursor, 10));
        assertInvalidCursor(() -> service.listReplies(postId, rootCommentId, rootCursor, 10));

        verifyNoInteractions(commentContentRepository, postContentRepository, commentPageCache);
    }

    @Test
    void legacyPageMethodsShouldContinueUsingOffsetRepositoryQueriesDirectly() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        CommentReadApplicationService service = service(
                commentContentRepository, postContentRepository, commentPageCache, cursorCodec());
        UUID postId = uuid(100);
        UUID rootCommentId = uuid(200);
        Comment root = comment(
                uuid(201), uuid(7), postId, uuid(201), null, null, "root",
                Instant.parse("2026-07-06T13:00:00Z"));
        Comment reply = comment(
                uuid(202), uuid(8), postId, rootCommentId, rootCommentId, uuid(7), "reply",
                Instant.parse("2026-07-06T13:00:01Z"));
        when(commentContentRepository.listRootComments(postId, 0, 10)).thenReturn(List.of(root));
        when(commentContentRepository.listReplies(rootCommentId, 3, 5)).thenReturn(List.of(reply));

        List<CommentResult> roots = service.comments(postId, -2, 10);
        List<CommentResult> replies = service.replies(postId, rootCommentId, 3, 5);

        assertThat(roots).extracting(CommentResult::id).containsExactly(root.getId());
        assertThat(replies).extracting(CommentResult::id).containsExactly(reply.getId());
        verify(postContentRepository).getById(postId);
        verify(commentContentRepository).listRootComments(postId, 0, 10);
        verify(commentContentRepository).assertCommentBelongsToPost(postId, rootCommentId);
        verify(commentContentRepository).listReplies(rootCommentId, 3, 5);
        verifyNoInteractions(commentPageCache);
    }

    private static CommentReadApplicationService service(
            CommentContentRepository commentContentRepository,
            PostContentRepository postContentRepository,
            CommentPageCache commentPageCache,
            CommentCursorCodec cursorCodec
    ) {
        return new CommentReadApplicationService(
                commentContentRepository,
                postContentRepository,
                new SpringHtmlContentTextCodec(),
                cursorCodec,
                commentPageCache
        );
    }

    private static CommentCursorCodec cursorCodec() {
        return new CommentCursorCodec(new JacksonJsonCodec(JsonMappers.standard()));
    }

    private static void assertInvalidCursor(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(INVALID_ARGUMENT));
    }

    private static CommentResult commentResult(UUID postId) {
        return new CommentResult(
                uuid(200),
                uuid(7),
                postId,
                uuid(200),
                null,
                null,
                "cached",
                Date.from(Instant.parse("2026-07-06T13:00:00Z")),
                null,
                0
        );
    }

    private static Comment comment(
            UUID id,
            UUID userId,
            UUID postId,
            UUID rootCommentId,
            UUID parentCommentId,
            UUID replyToUserId,
            String content,
            Instant createTime
    ) {
        return aComment()
                .id(id)
                .userId(userId)
                .postId(postId)
                .rootCommentId(rootCommentId)
                .parentCommentId(parentCommentId)
                .replyToUserId(replyToUserId)
                .content(content)
                .status(0)
                .createTime(Date.from(createTime))
                .build();
    }
}
