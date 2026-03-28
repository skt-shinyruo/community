package com.nowcoder.community.content.service;

import com.nowcoder.community.content.api.model.CommentView;
import com.nowcoder.community.content.api.query.CommentReadQueryApi;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.text.ContentTextCodec;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentReadQueryService implements CommentReadQueryApi {

    private final CommentService commentService;
    private final ContentTextCodec textCodec;

    public CommentReadQueryService(CommentService commentService, ContentTextCodec textCodec) {
        this.commentService = commentService;
        this.textCodec = textCodec;
    }

    @Override
    public List<CommentView> comments(int postId, Integer page, Integer size) {
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

    @Override
    public List<CommentView> replies(int postId, int commentId, Integer page, Integer size) {
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
}
