package com.nowcoder.community.content.service;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.api.model.ResolvedContentRef;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.mapper.CommentMapper;
import com.nowcoder.community.content.mapper.DiscussPostMapper;
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

class ContentEntityQueryServiceTest {

    @Test
    void resolveShouldRejectInvalidEntityId() {
        ContentEntityQueryService service = new ContentEntityQueryService(
                mock(DiscussPostMapper.class),
                mock(CommentMapper.class)
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
        ContentEntityQueryService service = new ContentEntityQueryService(
                mock(DiscussPostMapper.class),
                mock(CommentMapper.class)
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
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        UUID postId = uuid(101);
        UUID userId = uuid(7);

        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(userId);
        post.setStatus(0);
        when(discussPostMapper.selectDiscussPostById(postId)).thenReturn(post);

        ContentEntityQueryService service = new ContentEntityQueryService(discussPostMapper, commentMapper);

        ResolvedContentRef resolved = service.resolve(EntityTypes.POST, postId);

        assertThat(resolved.entityUserId()).isEqualTo(userId);
        assertThat(resolved.postId()).isEqualTo(postId);
    }

    @Test
    void resolveShouldReturnRootPostForReplyChain() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        UUID replyId = uuid(301);
        UUID replyUserId = uuid(9);
        UUID parentId = uuid(201);
        UUID parentUserId = uuid(5);
        UUID postId = uuid(101);
        UUID postUserId = uuid(7);

        Comment reply = activeComment(replyId, replyUserId, EntityTypes.COMMENT, parentId);
        Comment parent = activeComment(parentId, parentUserId, EntityTypes.POST, postId);

        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(postUserId);
        post.setStatus(0);

        when(commentMapper.selectCommentById(replyId)).thenReturn(reply);
        when(commentMapper.selectCommentById(parentId)).thenReturn(parent);
        when(discussPostMapper.selectDiscussPostById(postId)).thenReturn(post);

        ContentEntityQueryService service = new ContentEntityQueryService(discussPostMapper, commentMapper);

        ResolvedContentRef resolved = service.resolve(EntityTypes.COMMENT, replyId);

        assertThat(resolved.entityUserId()).isEqualTo(replyUserId);
        assertThat(resolved.postId()).isEqualTo(postId);
    }

    @Test
    void resolveShouldRejectDeletedPost() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        UUID postId = uuid(101);
        UUID userId = uuid(7);

        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(userId);
        post.setStatus(2);
        when(discussPostMapper.selectDiscussPostById(postId)).thenReturn(post);

        ContentEntityQueryService service = new ContentEntityQueryService(discussPostMapper, commentMapper);

        assertThatThrownBy(() -> service.resolve(EntityTypes.POST, postId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(POST_NOT_FOUND));
    }

    @Test
    void resolveShouldRejectDeletedComment() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        UUID commentId = uuid(301);
        UUID postId = uuid(101);

        Comment deleted = activeComment(commentId, uuid(9), EntityTypes.POST, postId);
        deleted.setStatus(2);
        when(commentMapper.selectCommentById(commentId)).thenReturn(deleted);

        ContentEntityQueryService service = new ContentEntityQueryService(discussPostMapper, commentMapper);

        assertThatThrownBy(() -> service.resolve(EntityTypes.COMMENT, commentId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(COMMENT_NOT_FOUND));
    }

    @Test
    void resolveShouldRejectMissingCommentParent() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        UUID replyId = uuid(301);
        UUID parentId = uuid(201);

        Comment reply = activeComment(replyId, uuid(9), EntityTypes.COMMENT, parentId);

        when(commentMapper.selectCommentById(replyId)).thenReturn(reply);
        when(commentMapper.selectCommentById(parentId)).thenReturn(null);

        ContentEntityQueryService service = new ContentEntityQueryService(discussPostMapper, commentMapper);

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
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        UUID replyId = uuid(301);
        UUID parentId = uuid(201);
        UUID targetId = uuid(101);

        Comment reply = activeComment(replyId, uuid(9), EntityTypes.COMMENT, parentId);
        Comment parent = activeComment(parentId, uuid(5), EntityTypes.USER, targetId);

        when(commentMapper.selectCommentById(replyId)).thenReturn(reply);
        when(commentMapper.selectCommentById(parentId)).thenReturn(parent);

        ContentEntityQueryService service = new ContentEntityQueryService(discussPostMapper, commentMapper);

        assertThatThrownBy(() -> service.resolve(EntityTypes.COMMENT, replyId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertThat(businessException.getErrorCode()).isEqualTo(POST_NOT_FOUND);
                    assertThat(businessException).hasMessage("评论所属帖子不存在");
                });
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
}
