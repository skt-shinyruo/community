package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.CommentPageResult;
import com.nowcoder.community.content.application.result.CommentResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.infrastructure.text.SpringHtmlContentTextCodec;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentReadApplicationServiceTest {

    @Test
    void listRootCommentsShouldReturnCursorPageOfTopLevelThreads() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        CommentReadApplicationService service = new CommentReadApplicationService(
                commentContentRepository,
                new SpringHtmlContentTextCodec(),
                new FeedCursorCodec()
        );
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
        verify(commentContentRepository).listRootComments(postId, 0, 2);
    }

    @Test
    void listRepliesShouldReturnCursorPageScopedByRootComment() {
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        CommentReadApplicationService service = new CommentReadApplicationService(
                commentContentRepository,
                new SpringHtmlContentTextCodec(),
                new FeedCursorCodec()
        );
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
