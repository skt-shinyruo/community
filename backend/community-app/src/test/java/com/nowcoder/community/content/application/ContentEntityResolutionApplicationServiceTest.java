package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.result.ResolvedContentResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.content.support.CommentTestBuilder.aComment;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;
import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ContentEntityResolutionApplicationServiceTest {

    @Test
    void resolveShouldRejectInvalidEntityId() {
        ContentEntityResolutionApplicationService service = service(
                mock(PostContentRepository.class),
                mock(CommentContentRepository.class)
        );

        assertThatThrownBy(() -> service.resolve(EntityTypes.POST, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertThat(businessException.getErrorCode()).isEqualTo(INVALID_ARGUMENT);
                    assertThat(businessException).hasMessage("entityId 非法");
                });
    }

    @Test
    void resolveShouldRejectInvalidEntityType() {
        ContentEntityResolutionApplicationService service = service(
                mock(PostContentRepository.class),
                mock(CommentContentRepository.class)
        );

        assertThatThrownBy(() -> service.resolve(EntityTypes.USER, uuid(1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertThat(businessException.getErrorCode()).isEqualTo(INVALID_ARGUMENT);
                    assertThat(businessException).hasMessage("仅支持解析 POST/COMMENT");
                });
    }

    @Test
    void resolveShouldReturnValidPost() {
        PostContentRepository postRepository = mock(PostContentRepository.class);
        CommentContentRepository commentRepository = mock(CommentContentRepository.class);
        UUID postId = uuid(101);
        UUID userId = uuid(7);
        when(postRepository.getById(postId)).thenReturn(activePost(postId, userId));

        ContentEntityResolutionApplicationService service = service(postRepository, commentRepository);

        ResolvedContentResult resolved = service.resolve(EntityTypes.POST, postId);

        assertThat(resolved.entityUserId()).isEqualTo(userId);
        assertThat(resolved.postId()).isEqualTo(postId);
    }

    @Test
    void resolveShouldReturnRootPostForReplyChain() {
        PostContentRepository postRepository = mock(PostContentRepository.class);
        CommentContentRepository commentRepository = mock(CommentContentRepository.class);
        UUID replyId = uuid(301);
        UUID replyUserId = uuid(9);
        UUID parentId = uuid(201);
        UUID postId = uuid(101);
        UUID postUserId = uuid(7);

        Comment reply = activeReplyComment(replyId, replyUserId, postId, parentId);

        when(commentRepository.getByIdAllowDeleted(replyId)).thenReturn(reply);
        when(postRepository.getById(postId)).thenReturn(activePost(postId, postUserId));

        ContentEntityResolutionApplicationService service = service(postRepository, commentRepository);

        ResolvedContentResult resolved = service.resolve(EntityTypes.COMMENT, replyId);

        assertThat(resolved.entityUserId()).isEqualTo(replyUserId);
        assertThat(resolved.postId()).isEqualTo(postId);
    }

    @Test
    void resolveShouldRejectDeletedPost() {
        PostContentRepository postRepository = mock(PostContentRepository.class);
        CommentContentRepository commentRepository = mock(CommentContentRepository.class);
        UUID postId = uuid(101);
        when(postRepository.getById(postId)).thenThrow(new BusinessException(POST_NOT_FOUND));

        ContentEntityResolutionApplicationService service = service(postRepository, commentRepository);

        assertThatThrownBy(() -> service.resolve(EntityTypes.POST, postId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(POST_NOT_FOUND));
    }

    @Test
    void resolveShouldRejectDeletedComment() {
        PostContentRepository postRepository = mock(PostContentRepository.class);
        CommentContentRepository commentRepository = mock(CommentContentRepository.class);
        UUID commentId = uuid(301);
        UUID postId = uuid(101);

        Comment deleted = aComment()
                .id(commentId)
                .userId(uuid(9))
                .postId(postId)
                .rootCommentId(commentId)
                .status(2)
                .build();
        when(commentRepository.getByIdAllowDeleted(commentId)).thenReturn(deleted);

        ContentEntityResolutionApplicationService service = service(postRepository, commentRepository);

        assertThatThrownBy(() -> service.resolve(EntityTypes.COMMENT, commentId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(COMMENT_NOT_FOUND));
    }

    @Test
    void resolveShouldRejectCommentWhosePostNoLongerExists() {
        PostContentRepository postRepository = mock(PostContentRepository.class);
        CommentContentRepository commentRepository = mock(CommentContentRepository.class);
        UUID replyId = uuid(301);
        UUID postId = uuid(101);

        Comment reply = activeReplyComment(replyId, uuid(9), postId, uuid(201));

        when(commentRepository.getByIdAllowDeleted(replyId)).thenReturn(reply);
        when(postRepository.getById(postId))
                .thenThrow(new BusinessException(POST_NOT_FOUND, "评论所属帖子不存在"));

        ContentEntityResolutionApplicationService service = service(postRepository, commentRepository);

        assertThatThrownBy(() -> service.resolve(EntityTypes.COMMENT, replyId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertThat(businessException.getErrorCode()).isEqualTo(POST_NOT_FOUND);
                    assertThat(businessException).hasMessage("评论所属帖子不存在");
                });
    }

    @Test
    void resolveShouldUseCanonicalPostIdWithoutTraversingMissingReplyParent() {
        PostContentRepository postRepository = mock(PostContentRepository.class);
        CommentContentRepository commentRepository = mock(CommentContentRepository.class);
        UUID replyId = uuid(301);
        UUID replyUserId = uuid(9);
        UUID postId = uuid(101);
        UUID parentId = uuid(201);

        Comment reply = activeReplyComment(replyId, replyUserId, postId, parentId);

        when(commentRepository.getByIdAllowDeleted(replyId)).thenReturn(reply);
        when(postRepository.getById(postId)).thenReturn(activePost(postId, uuid(7)));

        ContentEntityResolutionApplicationService service = service(postRepository, commentRepository);

        ResolvedContentResult resolved = service.resolve(EntityTypes.COMMENT, replyId);

        assertThat(resolved.entityUserId()).isEqualTo(replyUserId);
        assertThat(resolved.postId()).isEqualTo(postId);
        verify(commentRepository).getByIdAllowDeleted(replyId);
        verifyNoMoreInteractions(commentRepository);
    }

    private ContentEntityResolutionApplicationService service(
            PostContentRepository postRepository,
            CommentContentRepository commentRepository
    ) {
        return new ContentEntityResolutionApplicationService(postRepository, commentRepository);
    }

    private Comment activeReplyComment(UUID id, UUID userId, UUID postId, UUID parentCommentId) {
        return aComment()
                .id(id)
                .userId(userId)
                .postId(postId)
                .rootCommentId(parentCommentId)
                .parentCommentId(parentCommentId)
                .status(0)
                .build();
    }

    private DiscussPost activePost(UUID postId, UUID userId) {
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(userId);
        post.setStatus(0);
        return post;
    }
}
