package com.nowcoder.community.content.service;

import com.nowcoder.community.content.api.model.CommentView;
import com.nowcoder.community.content.api.query.CommentReadQueryApi;
import com.nowcoder.community.content.dto.CommentResponse;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.text.ContentTextCodec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CommentReadApplicationService implements CommentReadQueryApi {

    private final CommentService commentService;
    private final ContentTextCodec textCodec;

    public CommentReadApplicationService(CommentService commentService, ContentTextCodec textCodec) {
        this.commentService = commentService;
        this.textCodec = textCodec;
    }

    @Override
    public List<CommentView> comments(UUID postId, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        List<Comment> rows = commentService.listByPost(postId, p, s);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(this::toView)
                .toList();
    }

    public List<CommentResponse> commentResponses(UUID postId, Integer page, Integer size) {
        return comments(postId, page, size).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<CommentView> replies(UUID postId, UUID commentId, Integer page, Integer size) {
        commentService.assertCommentBelongsToPost(postId, commentId);
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        List<Comment> rows = commentService.listReplies(commentId, p, s);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(this::toView)
                .toList();
    }

    public List<CommentResponse> replyResponses(UUID postId, UUID commentId, Integer page, Integer size) {
        return replies(postId, commentId, page, size).stream()
                .map(this::toResponse)
                .toList();
    }

    private CommentView toView(Comment comment) {
        return new CommentView(
                comment.getId(),
                comment.getUserId(),
                comment.getEntityType(),
                comment.getEntityId(),
                comment.getTargetId(),
                textCodec.decodeOnRead(comment.getContent()),
                comment.getCreateTime(),
                comment.getUpdateTime(),
                comment.getEditCount()
        );
    }

    private CommentResponse toResponse(CommentView view) {
        CommentResponse response = new CommentResponse();
        response.setId(view.id());
        response.setUserId(view.userId());
        response.setEntityType(view.entityType());
        response.setEntityId(view.entityId());
        response.setTargetId(view.targetId());
        response.setContent(view.content());
        response.setCreateTime(view.createTime());
        response.setUpdateTime(view.updateTime());
        response.setEditCount(view.editCount());
        return response;
    }
}
