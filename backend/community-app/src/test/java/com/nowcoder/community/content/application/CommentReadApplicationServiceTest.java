package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
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

import static com.nowcoder.community.support.TestUuids.uuid;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommentReadApplicationServiceTest {

    @Test
    void listRootCommentsShouldReturnCursorPageOfTopLevelThreads() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        CommentReadApplicationService service = service(commentContentRepository, postContentRepository, commentPageCache);
        UUID postId = uuid(100);
        UUID rootCommentId = uuid(200);
        Comment rootComment = comment(rootCommentId, uuid(7), postId, rootCommentId, null, null, "root &amp; comment");
        when(commentContentRepository.listRootComments(postId, 0, 2)).thenReturn(List.of(rootComment));

        CommentPageResult page = service.listRootComments(postId, "", 2);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(rootCommentId);
            assertThat(item.postId()).isEqualTo(postId);
            assertThat(item.rootCommentId()).isEqualTo(rootCommentId);
            assertThat(item.parentCommentId()).isNull();
            assertThat(item.replyToUserId()).isNull();
            assertThat(item.content()).isEqualTo("root & comment");
        });
        assertThat(page.nextCursor()).isNotBlank();
        verify(postContentRepository).getById(postId);
        verify(commentContentRepository).listRootComments(postId, 0, 2);
        verify(commentPageCache).putRootPage(postId, "", 2, page);
    }

    @Test
    void listRootCommentsShouldServeFirstPageFromCache() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        CommentReadApplicationService service = service(commentContentRepository, postContentRepository, commentPageCache);
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
        CommentReadApplicationService service = service(commentContentRepository, postContentRepository, commentPageCache);
        UUID postId = uuid(100);

        when(postContentRepository.getById(postId)).thenThrow(new BusinessException(NOT_FOUND));

        assertThatThrownBy(() -> service.listRootComments(postId, "", 10))
                .isInstanceOf(BusinessException.class);
        verify(postContentRepository).getById(postId);
        verifyNoInteractions(commentPageCache, commentContentRepository);
    }

    @Test
    void listRootCommentsShouldNotUseFirstPageCacheForLaterPages() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        CommentPageCache commentPageCache = mock(CommentPageCache.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec();
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentReadApplicationService service = new CommentReadApplicationService(
                commentContentRepository,
                postContentRepository,
                new SpringHtmlContentTextCodec(),
                feedCursorCodec,
                commentPageCache
        );
        UUID postId = uuid(100);
        String cursor = feedCursorCodec.encodePage(1, 10);

        service.listRootComments(postId, cursor, 10);

        verify(postContentRepository).getById(postId);
        verify(commentContentRepository).listRootComments(postId, 1, 10);
        verifyNoInteractions(commentPageCache);
    }

    @Test
    void listRepliesShouldReturnCursorPageScopedByRootComment() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        CommentReadApplicationService service = service(commentContentRepository, mock(CommentPageCache.class));
        UUID postId = uuid(100);
        UUID rootCommentId = uuid(200);
        UUID replyId = uuid(201);
        UUID parentCommentId = uuid(202);
        UUID replyToUserId = uuid(9);
        Comment replyComment = comment(replyId, uuid(8), postId, rootCommentId, parentCommentId, replyToUserId, "reply");
        when(commentContentRepository.listReplies(rootCommentId, 0, 3)).thenReturn(List.of(replyComment));

        CommentPageResult page = service.listReplies(postId, rootCommentId, "", 3);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(replyId);
            assertThat(item.postId()).isEqualTo(postId);
            assertThat(item.rootCommentId()).isEqualTo(rootCommentId);
            assertThat(item.parentCommentId()).isEqualTo(parentCommentId);
            assertThat(item.replyToUserId()).isEqualTo(replyToUserId);
        });
        assertThat(page.nextCursor()).isNotBlank();
        verify(commentContentRepository).assertCommentBelongsToPost(postId, rootCommentId);
        verify(commentContentRepository).listReplies(rootCommentId, 0, 3);
    }

    private static CommentReadApplicationService service(
            CommentContentRepository commentContentRepository,
            CommentPageCache commentPageCache
    ) {
        return service(commentContentRepository, mock(PostContentRepository.class), commentPageCache);
    }

    private static CommentReadApplicationService service(
            CommentContentRepository commentContentRepository,
            PostContentRepository postContentRepository,
            CommentPageCache commentPageCache
    ) {
        return new CommentReadApplicationService(
                commentContentRepository,
                postContentRepository,
                new SpringHtmlContentTextCodec(),
                new FeedCursorCodec(),
                commentPageCache
        );
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
            String content
    ) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setUserId(userId);
        comment.setPostId(postId);
        comment.setRootCommentId(rootCommentId);
        comment.setParentCommentId(parentCommentId);
        comment.setReplyToUserId(replyToUserId);
        comment.setContent(content);
        comment.setStatus(0);
        comment.setCreateTime(Date.from(Instant.parse("2026-07-06T13:00:00Z")));
        return comment;
    }
}
