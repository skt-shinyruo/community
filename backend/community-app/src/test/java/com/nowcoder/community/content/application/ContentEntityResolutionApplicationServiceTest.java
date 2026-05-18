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
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;
import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
        UUID parentUserId = uuid(5);
        UUID postId = uuid(101);
        UUID postUserId = uuid(7);

        Comment reply = activeComment(replyId, replyUserId, EntityTypes.COMMENT, parentId);
        Comment parent = activeComment(parentId, parentUserId, EntityTypes.POST, postId);

        when(commentRepository.getByIdAllowDeleted(replyId)).thenReturn(reply);
        when(commentRepository.getByIdAllowDeleted(parentId)).thenReturn(parent);
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

        Comment deleted = activeComment(commentId, uuid(9), EntityTypes.POST, postId);
        deleted.setStatus(2);
        when(commentRepository.getByIdAllowDeleted(commentId)).thenReturn(deleted);

        ContentEntityResolutionApplicationService service = service(postRepository, commentRepository);

        assertThatThrownBy(() -> service.resolve(EntityTypes.COMMENT, commentId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(COMMENT_NOT_FOUND));
    }

    @Test
    void resolveShouldRejectMissingCommentParent() {
        PostContentRepository postRepository = mock(PostContentRepository.class);
        CommentContentRepository commentRepository = mock(CommentContentRepository.class);
        UUID replyId = uuid(301);
        UUID parentId = uuid(201);

        Comment reply = activeComment(replyId, uuid(9), EntityTypes.COMMENT, parentId);

        when(commentRepository.getByIdAllowDeleted(replyId)).thenReturn(reply);
        when(commentRepository.getByIdAllowDeleted(parentId)).thenThrow(new BusinessException(COMMENT_NOT_FOUND));

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
    void resolveShouldRejectBrokenReplyChain() {
        PostContentRepository postRepository = mock(PostContentRepository.class);
        CommentContentRepository commentRepository = mock(CommentContentRepository.class);
        UUID replyId = uuid(301);
        UUID parentId = uuid(201);
        UUID targetId = uuid(101);

        Comment reply = activeComment(replyId, uuid(9), EntityTypes.COMMENT, parentId);
        Comment parent = activeComment(parentId, uuid(5), EntityTypes.USER, targetId);

        when(commentRepository.getByIdAllowDeleted(replyId)).thenReturn(reply);
        when(commentRepository.getByIdAllowDeleted(parentId)).thenReturn(parent);

        ContentEntityResolutionApplicationService service = service(postRepository, commentRepository);

        assertThatThrownBy(() -> service.resolve(EntityTypes.COMMENT, replyId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertThat(businessException.getErrorCode()).isEqualTo(POST_NOT_FOUND);
                    assertThat(businessException).hasMessage("评论所属帖子不存在");
                });
    }

    private ContentEntityResolutionApplicationService service(
            PostContentRepository postRepository,
            CommentContentRepository commentRepository
    ) {
        return new ContentEntityResolutionApplicationService(postRepository, commentRepository);
    }

    private Comment activeComment(UUID id, UUID userId, int entityType, UUID entityId) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setUserId(userId);
        comment.setEntityType(entityType);
        comment.setEntityId(entityId);
        comment.setStatus(0);
        return comment;
    }

    private DiscussPost activePost(UUID postId, UUID userId) {
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(userId);
        post.setStatus(0);
        return post;
    }
}
