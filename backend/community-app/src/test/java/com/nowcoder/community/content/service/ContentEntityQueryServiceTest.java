package com.nowcoder.community.content.service;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.api.model.ResolvedContentRef;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.mapper.CommentMapper;
import com.nowcoder.community.content.mapper.DiscussPostMapper;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;
import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;
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

        assertThatThrownBy(() -> service.resolve(EntityTypes.POST, 0))
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

        assertThatThrownBy(() -> service.resolve(EntityTypes.USER, 1))
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

        DiscussPost post = new DiscussPost();
        post.setId(101);
        post.setUserId(7);
        post.setStatus(0);
        when(discussPostMapper.selectDiscussPostById(101)).thenReturn(post);

        ContentEntityQueryService service = new ContentEntityQueryService(discussPostMapper, commentMapper);

        ResolvedContentRef resolved = service.resolve(EntityTypes.POST, 101);

        assertThat(resolved.entityUserId()).isEqualTo(7);
        assertThat(resolved.postId()).isEqualTo(101);
    }

    @Test
    void resolveShouldReturnRootPostForReplyChain() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);

        Comment reply = activeComment(301, 9, EntityTypes.COMMENT, 201);
        Comment parent = activeComment(201, 5, EntityTypes.POST, 101);

        DiscussPost post = new DiscussPost();
        post.setId(101);
        post.setUserId(7);
        post.setStatus(0);

        when(commentMapper.selectCommentById(301)).thenReturn(reply);
        when(commentMapper.selectCommentById(201)).thenReturn(parent);
        when(discussPostMapper.selectDiscussPostById(101)).thenReturn(post);

        ContentEntityQueryService service = new ContentEntityQueryService(discussPostMapper, commentMapper);

        ResolvedContentRef resolved = service.resolve(EntityTypes.COMMENT, 301);

        assertThat(resolved.entityUserId()).isEqualTo(9);
        assertThat(resolved.postId()).isEqualTo(101);
    }

    @Test
    void resolveShouldRejectDeletedPost() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);

        DiscussPost post = new DiscussPost();
        post.setId(101);
        post.setUserId(7);
        post.setStatus(2);
        when(discussPostMapper.selectDiscussPostById(101)).thenReturn(post);

        ContentEntityQueryService service = new ContentEntityQueryService(discussPostMapper, commentMapper);

        assertThatThrownBy(() -> service.resolve(EntityTypes.POST, 101))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(POST_NOT_FOUND));
    }

    @Test
    void resolveShouldRejectDeletedComment() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);

        Comment deleted = activeComment(301, 9, EntityTypes.POST, 101);
        deleted.setStatus(2);
        when(commentMapper.selectCommentById(301)).thenReturn(deleted);

        ContentEntityQueryService service = new ContentEntityQueryService(discussPostMapper, commentMapper);

        assertThatThrownBy(() -> service.resolve(EntityTypes.COMMENT, 301))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(COMMENT_NOT_FOUND));
    }

    @Test
    void resolveShouldRejectMissingCommentParent() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);

        Comment reply = activeComment(301, 9, EntityTypes.COMMENT, 201);

        when(commentMapper.selectCommentById(301)).thenReturn(reply);
        when(commentMapper.selectCommentById(201)).thenReturn(null);

        ContentEntityQueryService service = new ContentEntityQueryService(discussPostMapper, commentMapper);

        assertThatThrownBy(() -> service.resolve(EntityTypes.COMMENT, 301))
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

        Comment reply = activeComment(301, 9, EntityTypes.COMMENT, 201);
        Comment parent = activeComment(201, 5, EntityTypes.USER, 101);

        when(commentMapper.selectCommentById(301)).thenReturn(reply);
        when(commentMapper.selectCommentById(201)).thenReturn(parent);

        ContentEntityQueryService service = new ContentEntityQueryService(discussPostMapper, commentMapper);

        assertThatThrownBy(() -> service.resolve(EntityTypes.COMMENT, 301))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertThat(businessException.getErrorCode()).isEqualTo(POST_NOT_FOUND);
                    assertThat(businessException).hasMessage("评论所属帖子不存在");
                });
    }

    private Comment activeComment(int id, int userId, int entityType, int entityId) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setUserId(userId);
        comment.setEntityType(entityType);
        comment.setEntityId(entityId);
        comment.setStatus(0);
        return comment;
    }
}
