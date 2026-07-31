package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.query.PostReadQueryApi;
import com.nowcoder.community.content.api.query.PostReadQueryApi.PostSummaryView;
import com.nowcoder.community.content.api.query.PostReadQueryApi.RecentUserCommentView;
import com.nowcoder.community.content.application.PostReadApplicationService;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.application.result.RecentUserCommentResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class PostReadQueryApiAdapter implements PostReadQueryApi {

    private final PostReadApplicationService delegate;

    public PostReadQueryApiAdapter(PostReadApplicationService delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<PostSummaryView> listPostsByUser(UUID userId, Integer page, Integer size) {
        return delegate.listPostsByUser(userId, page, size).stream()
                .map(PostReadQueryApiAdapter::toPostSummaryView)
                .toList();
    }

    @Override
    public List<RecentUserCommentView> listRecentCommentsByUser(UUID userId, Integer page, Integer size) {
        return delegate.listRecentCommentsByUser(userId, page, size).stream()
                .map(PostReadQueryApiAdapter::toRecentUserCommentView)
                .toList();
    }

    private static PostSummaryView toPostSummaryView(PostSummaryResult result) {
        if (result == null) {
            return null;
        }
        return new PostSummaryView(
                result.id(),
                result.userId(),
                result.title(),
                result.type(),
                result.status(),
                result.createTime(),
                result.commentCount(),
                result.score(),
                result.categoryId(),
                result.tags(),
                result.lastReplyUserId(),
                result.lastReplyTime(),
                result.lastActivityTime(),
                result.lastReplyPreview()
        );
    }

    private static RecentUserCommentView toRecentUserCommentView(RecentUserCommentResult result) {
        if (result == null) {
            return null;
        }
        return new RecentUserCommentView(
                result.id(),
                result.userId(),
                result.entityType(),
                result.entityId(),
                result.targetId(),
                result.postId(),
                result.postTitle(),
                result.content(),
                result.createTime()
        );
    }
}
