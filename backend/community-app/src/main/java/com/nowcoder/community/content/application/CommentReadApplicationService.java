package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.application.result.CommentResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.application.ContentTextCodec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CommentReadApplicationService {

    private final CommentContentRepository commentContentPort;
    private final ContentTextCodec textCodec;

    public CommentReadApplicationService(CommentContentRepository commentContentPort, ContentTextCodec textCodec) {
        this.commentContentPort = commentContentPort;
        this.textCodec = textCodec;
    }

    public List<CommentResult> comments(UUID postId, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        List<Comment> rows = commentContentPort.listByPost(postId, p, s);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(this::toResult)
                .toList();
    }

    public List<CommentResult> replies(UUID postId, UUID commentId, Integer page, Integer size) {
        commentContentPort.assertCommentBelongsToPost(postId, commentId);
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        List<Comment> rows = commentContentPort.listReplies(commentId, p, s);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(this::toResult)
                .toList();
    }

    private CommentResult toResult(Comment comment) {
        return new CommentResult(
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
