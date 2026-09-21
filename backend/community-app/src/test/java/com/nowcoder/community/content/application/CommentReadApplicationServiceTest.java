package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.content.application.result.CommentPageResult;
import com.nowcoder.community.content.application.result.CommentResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.infrastructure.text.SpringHtmlContentTextCodec;
import com.nowcoder.community.social.api.query.SocialLikeQueryApi;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.COMMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;
import static com.nowcoder.community.content.domain.repository.CommentContentRepository.ROOT_ORDER_EARLIEST;
import static com.nowcoder.community.content.domain.repository.CommentContentRepository.ROOT_ORDER_LATEST;
import static com.nowcoder.community.content.support.CommentTestBuilder.aComment;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when(commentContentRepository.listRootCommentsAfter(postId, null, null, ROOT_ORDER_LATEST, 3))
                .thenReturn(List.of(firstComment, secondComment, probeComment));

        CommentPageResult page = service.listRootComments(postId, "latest", "", 2);

        assertThat(page.items()).extracting(CommentResult::id)
                .containsExactly(firstCommentId, secondCommentId);
        assertThat(page.items().get(0)).satisfies(item -> {
            assertThat(item.postId()).isEqualTo(postId);
            assertThat(item.rootCommentId()).isEqualTo(firstCommentId);
            assertThat(item.parentCommentId()).isNull();
            assertThat(item.replyToUserId()).isNull();
            assertThat(item.content()).isEqualTo("root & comment");
        });
        assertThat(cursorCodec.decodeRoot(page.nextCursor(), postId, CommentSort.LATEST))
                .contains(new CommentCursorCodec.Boundary(0L, secondTime, secondCommentId));
        verify(postContentRepository).getById(postId);
        verify(commentContentRepository).listRootCommentsAfter(postId, null, null, ROOT_ORDER_LATEST, 3);
        verify(commentPageCache).putRootPage(postId, CommentSort.LATEST, "", 2, page);
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
        when(commentContentRepository.listRootCommentsAfter(postId, null, null, ROOT_ORDER_LATEST, 3))
                .thenReturn(List.of(firstComment, secondComment));

        CommentPageResult page = service.listRootComments(postId, "", "", 2);

        assertThat(page.items()).extracting(CommentResult::id)
                .containsExactly(firstCommentId, secondCommentId);
        assertThat(page.nextCursor()).isBlank();
        verify(postContentRepository).getById(postId);
        verify(commentContentRepository).listRootCommentsAfter(postId, null, null, ROOT_ORDER_LATEST, 3);
        verify(commentPageCache).putRootPage(postId, CommentSort.LATEST, "", 2, page);
    }

    @Test
    void listRootCommentsShouldServeFirstPageFromCacheForItsSort() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        CommentReadApplicationService service = service(
                commentContentRepository, postContentRepository, commentPageCache, cursorCodec());
        UUID postId = uuid(100);
        CommentPageResult cached = new CommentPageResult(List.of(commentResult(postId)), "next");
        when(commentPageCache.getRootPage(postId, CommentSort.HOT, "", 10)).thenReturn(cached);

        CommentPageResult result = service.listRootComments(postId, "hot", "", 10);

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

        assertThatThrownBy(() -> service.listRootComments(postId, "", "", 10))
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
        String cursor = cursorCodec.encodeRoot(postId, CommentSort.LATEST, 0L, boundaryTime, boundaryId);

        service.listRootComments(postId, "latest", cursor, 10);

        verify(postContentRepository).getById(postId);
        verify(commentContentRepository).listRootCommentsAfter(
                postId, Date.from(boundaryTime), boundaryId, ROOT_ORDER_LATEST, 11);
        verifyNoInteractions(commentPageCache);
    }

    @Test
    void earliestSortShouldQueryAscendingKeysetWithItsOwnCursorScope() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        CommentCursorCodec cursorCodec = cursorCodec();
        CommentReadApplicationService service = service(
                commentContentRepository, postContentRepository, commentPageCache, cursorCodec);
        UUID postId = uuid(100);
        UUID oldestCommentId = uuid(220);
        UUID secondCommentId = uuid(221);
        Instant oldestTime = Instant.parse("2026-07-06T12:00:00Z");
        Comment oldestComment = comment(
                oldestCommentId, uuid(7), postId, oldestCommentId, null, null, "oldest", oldestTime);
        Comment secondComment = comment(
                secondCommentId, uuid(8), postId, secondCommentId, null, null, "second",
                Instant.parse("2026-07-06T12:30:00Z"));
        Comment probeComment = comment(
                uuid(223), uuid(9), postId, uuid(223), null, null, "probe",
                Instant.parse("2026-07-06T13:00:00Z"));
        when(commentContentRepository.listRootCommentsAfter(postId, null, null, ROOT_ORDER_EARLIEST, 3))
                .thenReturn(List.of(oldestComment, secondComment, probeComment));

        CommentPageResult page = service.listRootComments(postId, "earliest", "", 2);

        assertThat(page.items()).extracting(CommentResult::id)
                .containsExactly(oldestCommentId, secondCommentId);
        assertThat(cursorCodec.decodeRoot(page.nextCursor(), postId, CommentSort.EARLIEST))
                .contains(new CommentCursorCodec.Boundary(0L, Instant.parse("2026-07-06T12:30:00Z"), secondCommentId));
        verify(commentContentRepository).listRootCommentsAfter(postId, null, null, ROOT_ORDER_EARLIEST, 3);
        verify(commentPageCache).putRootPage(postId, CommentSort.EARLIEST, "", 2, page);
    }

    @Test
    void hotSortShouldRankByLikeCountThenTimeAndIdAndCarryRankCursor() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        SocialLikeQueryApi likeQueryApi = mock(SocialLikeQueryApi.class);
        CommentCursorCodec cursorCodec = cursorCodec();
        CommentReadApplicationService service = new CommentReadApplicationService(
                commentContentRepository,
                postContentRepository,
                likeQueryApi,
                new SpringHtmlContentTextCodec(),
                cursorCodec,
                commentPageCache
        );
        UUID postId = uuid(100);
        UUID topCommentId = uuid(230);
        UUID secondCommentId = uuid(231);
        UUID zeroLikeId = uuid(232);
        Instant topTime = Instant.parse("2026-07-06T13:30:00Z");
        Instant secondTime = Instant.parse("2026-07-06T13:00:00Z");
        Instant zeroTime = Instant.parse("2026-07-06T14:00:00Z");
        Comment topComment = comment(
                topCommentId, uuid(7), postId, topCommentId, null, null, "top", topTime);
        Comment secondComment = comment(
                secondCommentId, uuid(8), postId, secondCommentId, null, null, "second", secondTime);
        Comment zeroLike = comment(
                zeroLikeId, uuid(9), postId, zeroLikeId, null, null, "zero", zeroTime);
        when(commentContentRepository.listRootCommentsAfter(postId, null, null, ROOT_ORDER_LATEST, 200))
                .thenReturn(List.of(zeroLike, topComment, secondComment));
        when(likeQueryApi.counts(eq(COMMENT), anyList())).thenReturn(Map.of(
                topCommentId, 5L,
                secondCommentId, 5L,
                zeroLikeId, 0L
        ));

        CommentPageResult page = service.listRootComments(postId, "hot", "", 2);

        // 赞数优先：5 赞的两条按时间倒序在前（尽管 0 赞更新），0 赞垫底被探针语义截断。
        assertThat(page.items()).extracting(CommentResult::id)
                .containsExactly(topCommentId, secondCommentId);
        assertThat(cursorCodec.decodeRoot(page.nextCursor(), postId, CommentSort.HOT))
                .contains(new CommentCursorCodec.Boundary(5L, secondTime, secondCommentId));
        verify(commentPageCache).putRootPage(postId, CommentSort.HOT, "", 2, page);
    }

    @Test
    void hotSortNextPageShouldDropRowsNotStrictlyBelowBoundaryRank() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        SocialLikeQueryApi likeQueryApi = mock(SocialLikeQueryApi.class);
        CommentCursorCodec cursorCodec = cursorCodec();
        CommentReadApplicationService service = new CommentReadApplicationService(
                commentContentRepository,
                postContentRepository,
                likeQueryApi,
                new SpringHtmlContentTextCodec(),
                cursorCodec,
                commentPageCache
        );
        UUID postId = uuid(100);
        UUID boundaryId = uuid(240);
        UUID sameRankOlderId = uuid(241);
        UUID lowerRankId = uuid(242);
        Instant boundaryTime = Instant.parse("2026-07-06T13:10:00Z");
        Instant olderTime = Instant.parse("2026-07-06T13:00:00Z");
        // 边界：(5, 13:10, 240)。同赞数但时间更早的行仍属于下一页；更低赞数的行也属于下一页。
        String cursor = cursorCodec.encodeRoot(postId, CommentSort.HOT, 5L, boundaryTime, boundaryId);
        Comment sameRankOlder = comment(
                sameRankOlderId, uuid(7), postId, sameRankOlderId, null, null, "same rank older", olderTime);
        Comment lowerRank = comment(
                lowerRankId, uuid(8), postId, lowerRankId, null, null, "lower rank", boundaryTime);
        when(commentContentRepository.listRootCommentsAfter(postId, null, null, ROOT_ORDER_LATEST, 200))
                .thenReturn(List.of(sameRankOlder, lowerRank));
        when(likeQueryApi.counts(eq(COMMENT), anyList())).thenReturn(Map.of(
                sameRankOlderId, 5L,
                lowerRankId, 2L
        ));

        CommentPageResult page = service.listRootComments(postId, "hot", cursor, 2);

        assertThat(page.items()).extracting(CommentResult::id)
                .containsExactly(sameRankOlderId, lowerRankId);
        assertThat(page.nextCursor()).isBlank();
        verify(commentPageCache, never()).putRootPage(anyUUID(), anySort(), anyString(), anyInt(), any());
    }

    @Test
    void hotSortNextPageShouldFetchTheSameFixedWindowWithoutTimeBoundary() {
        // 回归：旧实现第 2 页按时间边界回源，窗口外/时间更新的行会被静默丢弃。
        // 固定窗口语义下，翻页始终重取同一候选集，仅由 rank 游标裁剪。
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        SocialLikeQueryApi likeQueryApi = mock(SocialLikeQueryApi.class);
        CommentCursorCodec cursorCodec = cursorCodec();
        CommentReadApplicationService service = new CommentReadApplicationService(
                commentContentRepository,
                postContentRepository,
                likeQueryApi,
                new SpringHtmlContentTextCodec(),
                cursorCodec,
                commentPageCache
        );
        UUID postId = uuid(100);
        UUID newestLowLikeId = uuid(260);
        UUID oldHighLikeId = uuid(261);
        Instant newestTime = Instant.parse("2026-07-06T15:00:00Z");
        Instant oldTime = Instant.parse("2026-07-06T12:00:00Z");
        // 第一页：最新但低赞的行在前（时间序窗口内），rank 游标指向它。
        Comment newestLowLike = comment(
                newestLowLikeId, uuid(7), postId, newestLowLikeId, null, null, "newest low like", newestTime);
        Comment oldHighLike = comment(
                oldHighLikeId, uuid(8), postId, oldHighLikeId, null, null, "old high like", oldTime);
        when(commentContentRepository.listRootCommentsAfter(postId, null, null, ROOT_ORDER_LATEST, 200))
                .thenReturn(List.of(newestLowLike, oldHighLike));
        when(likeQueryApi.counts(eq(COMMENT), anyList())).thenReturn(Map.of(
                newestLowLikeId, 0L,
                oldHighLikeId, 9L
        ));
        CommentPageResult firstPage = service.listRootComments(postId, "hot", "", 1);
        assertThat(firstPage.items()).extracting(CommentResult::id).containsExactly(oldHighLikeId);
        String cursor = firstPage.nextCursor();
        assertThat(cursor).isNotBlank();

        // 第二页：时间上更旧（时间 keyset 会丢弃）但赞数更低的行必须仍然出现。
        CommentPageResult secondPage = service.listRootComments(postId, "hot", cursor, 1);

        assertThat(secondPage.items()).extracting(CommentResult::id).containsExactly(newestLowLikeId);
        assertThat(secondPage.nextCursor()).isBlank();
    }

    @Test
    void hotSortShouldTolerateLikeCountReadFailureWithoutFailingThePage() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        SocialLikeQueryApi likeQueryApi = mock(SocialLikeQueryApi.class);
        CommentCursorCodec cursorCodec = cursorCodec();
        CommentReadApplicationService service = new CommentReadApplicationService(
                commentContentRepository,
                postContentRepository,
                likeQueryApi,
                new SpringHtmlContentTextCodec(),
                cursorCodec,
                commentPageCache
        );
        UUID postId = uuid(100);
        UUID commentId = uuid(250);
        Comment onlyComment = comment(
                commentId, uuid(7), postId, commentId, null, null, "only",
                Instant.parse("2026-07-06T13:00:00Z"));
        when(commentContentRepository.listRootCommentsAfter(postId, null, null, ROOT_ORDER_LATEST, 200))
                .thenReturn(List.of(onlyComment));
        when(likeQueryApi.counts(eq(COMMENT), anyList()))
                .thenThrow(new RuntimeException("social unavailable"));

        CommentPageResult page = service.listRootComments(postId, "hot", "", 2);

        assertThat(page.items()).extracting(CommentResult::id).containsExactly(commentId);
        assertThat(page.nextCursor()).isBlank();
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
                uuid(101), CommentSort.LATEST, 0L, Instant.parse("2026-07-06T13:00:00Z"), uuid(201));
        String hotCursor = cursorCodec.encodeRoot(
                postId, CommentSort.HOT, 3L, Instant.parse("2026-07-06T13:00:00Z"), uuid(202));

        assertInvalidCursor(() -> service.listRootComments(postId, "latest", "%%%", 10));
        assertInvalidCursor(() -> service.listRootComments(postId, "latest", crossPostCursor, 10));
        assertInvalidCursor(() -> service.listRootComments(postId, "latest", hotCursor, 10));

        verifyNoInteractions(commentContentRepository, postContentRepository, commentPageCache);
    }

    @Test
    void unknownSortShouldFailAsInvalidArgument() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        CommentReadApplicationService service = service(
                commentContentRepository, postContentRepository, commentPageCache, cursorCodec());
        UUID postId = uuid(100);

        assertInvalidCursor(() -> service.listRootComments(postId, "popular", "", 10));

        verifyNoInteractions(commentContentRepository, postContentRepository, commentPageCache);
    }

    @Test
    void dateUnrepresentableRootCursorShouldFailBeforeRepositoryCalls() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        CommentReadApplicationService service = service(
                commentContentRepository, postContentRepository, commentPageCache, cursorCodec());
        UUID postId = uuid(100);
        String cursor = forgeCursor("ROOT", postId, null, "HOT", 0L, Instant.MAX, uuid(201));

        assertInvalidCursor(() -> service.listRootComments(postId, "hot", cursor, 10));

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
        String rootCursor = cursorCodec.encodeRoot(postId, CommentSort.LATEST, 0L, boundaryTime, uuid(203));

        assertInvalidCursor(() -> service.listReplies(postId, rootCommentId, crossRootCursor, 10));
        assertInvalidCursor(() -> service.listReplies(postId, rootCommentId, rootCursor, 10));

        verifyNoInteractions(commentContentRepository, postContentRepository, commentPageCache);
    }

    @Test
    void mysqlOutOfRangeReplyCursorShouldFailBeforeRepositoryCalls() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        CommentReadApplicationService service = service(
                commentContentRepository, postContentRepository, commentPageCache, cursorCodec());
        UUID postId = uuid(100);
        UUID rootCommentId = uuid(200);
        String cursor = forgeCursor(
                "REPLY",
                postId,
                rootCommentId,
                null,
                0L,
                Instant.parse("2038-01-19T03:14:08Z"),
                uuid(201)
        );

        assertInvalidCursor(() -> service.listReplies(postId, rootCommentId, cursor, 10));

        verifyNoInteractions(commentContentRepository, postContentRepository, commentPageCache);
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
                mock(SocialLikeQueryApi.class),
                new SpringHtmlContentTextCodec(),
                cursorCodec,
                commentPageCache
        );
    }

    private static CommentCursorCodec cursorCodec() {
        return new CommentCursorCodec(new JacksonJsonCodec(JacksonJsonCodec.standardMapper()));
    }

    private static String forgeCursor(
            String kind,
            UUID postId,
            UUID rootCommentId,
            String sort,
            long likeCount,
            Instant createTime,
            UUID commentId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", 2);
        payload.put("kind", kind);
        payload.put("postId", postId.toString());
        payload.put("rootCommentId", rootCommentId == null ? null : rootCommentId.toString());
        payload.put("sort", sort);
        payload.put("likeCount", likeCount);
        payload.put("createTime", createTime.toString());
        payload.put("commentId", commentId.toString());
        String json = new JacksonJsonCodec(JacksonJsonCodec.standardMapper()).toJson(payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertInvalidCursor(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(INVALID_ARGUMENT));
    }

    private static UUID anyUUID() {
        return org.mockito.ArgumentMatchers.any(UUID.class);
    }

    private static CommentSort anySort() {
        return org.mockito.ArgumentMatchers.any(CommentSort.class);
    }

    private static String anyString() {
        return org.mockito.ArgumentMatchers.anyString();
    }

    private static CommentPageResult any() {
        return org.mockito.ArgumentMatchers.any(CommentPageResult.class);
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
