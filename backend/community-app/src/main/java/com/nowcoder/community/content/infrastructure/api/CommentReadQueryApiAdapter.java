package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.model.CommentView;
import com.nowcoder.community.content.api.query.CommentReadQueryApi;
import com.nowcoder.community.content.application.CommentReadApplicationService;
import com.nowcoder.community.content.application.result.CommentResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CommentReadQueryApiAdapter implements CommentReadQueryApi {

    private final CommentReadApplicationService delegate;

    public CommentReadQueryApiAdapter(CommentReadApplicationService delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<CommentView> comments(UUID postId, Integer page, Integer size) {
        return delegate.comments(postId, page, size).stream()
                .map(CommentReadQueryApiAdapter::toCommentView)
                .toList();
    }

    @Override
    public List<CommentView> replies(UUID postId, UUID commentId, Integer page, Integer size) {
        return delegate.replies(postId, commentId, page, size).stream()
                .map(CommentReadQueryApiAdapter::toCommentView)
                .toList();
    }

    private static CommentView toCommentView(CommentResult result) {
        if (result == null) {
            return null;
        }
        return new CommentView(
                result.id(),
                result.userId(),
                result.entityType(),
                result.entityId(),
                result.targetId(),
                result.content(),
                result.createTime(),
                result.updateTime(),
                result.editCount()
        );
    }
}
