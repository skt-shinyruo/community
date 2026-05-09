package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.model.PostContentBlockView;
import com.nowcoder.community.content.api.model.PostDetailView;
import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.api.model.RecentUserCommentView;
import com.nowcoder.community.content.api.query.PostReadQueryApi;
import com.nowcoder.community.content.application.PostReadApplicationService;
import com.nowcoder.community.content.application.result.PostContentBlockResult;
import com.nowcoder.community.content.application.result.PostDetailResult;
import com.nowcoder.community.content.application.result.PostMediaViewResult;
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
    public List<PostSummaryView> listPosts(UUID currentUserId, String order, UUID categoryId, String tag, Boolean subscribed, Integer page, Integer size) {
        return delegate.listPosts(currentUserId, order, categoryId, tag, subscribed, page, size).stream()
                .map(PostReadQueryApiAdapter::toPostSummaryView)
                .toList();
    }

    @Override
    public List<PostSummaryView> listPostsByUser(UUID userId, Integer page, Integer size) {
        return delegate.listPostsByUser(userId, page, size).stream()
                .map(PostReadQueryApiAdapter::toPostSummaryView)
                .toList();
    }

    @Override
    public List<PostSummaryView> listPostsByIds(List<UUID> postIds) {
        return delegate.listPostsByIds(postIds).stream()
                .map(PostReadQueryApiAdapter::toPostSummaryView)
                .toList();
    }

    @Override
    public PostDetailView getPostDetail(UUID currentUserId, UUID postId) {
        return toPostDetailView(delegate.getPostDetail(currentUserId, postId));
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
                result.preview(),
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

    private static PostDetailView toPostDetailView(PostDetailResult result) {
        if (result == null) {
            return null;
        }
        return new PostDetailView(
                result.id(),
                result.userId(),
                result.title(),
                toPostContentBlockViews(result.blocks()),
                result.type(),
                result.status(),
                result.createTime(),
                result.updateTime(),
                result.editCount(),
                result.commentCount(),
                result.score(),
                result.categoryId(),
                result.tags(),
                result.likeCount(),
                result.liked(),
                result.bookmarked()
        );
    }

    private static List<PostContentBlockView> toPostContentBlockViews(List<PostContentBlockResult> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        return blocks.stream()
                .map(PostReadQueryApiAdapter::toPostContentBlockView)
                .toList();
    }

    private static PostContentBlockView toPostContentBlockView(PostContentBlockResult result) {
        if (result == null) {
            return null;
        }
        return new PostContentBlockView(
                result.id(),
                result.index(),
                result.type(),
                result.text(),
                result.assetId(),
                result.language(),
                result.caption(),
                result.displayName(),
                result.metadata(),
                toPostMediaView(result.media())
        );
    }

    private static PostContentBlockView.PostMediaView toPostMediaView(PostMediaViewResult result) {
        if (result == null) {
            return null;
        }
        return new PostContentBlockView.PostMediaView(
                result.assetId(),
                result.mediaKind(),
                result.lifecycle(),
                result.videoState(),
                result.fileName(),
                result.contentType(),
                result.contentLength(),
                result.url(),
                result.downloadUrl(),
                result.posterUrl(),
                result.sources().stream()
                        .map(source -> new PostContentBlockView.PostMediaView.VideoSource(
                                source.url(),
                                source.contentType(),
                                source.width(),
                                source.height()
                        ))
                        .toList()
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
