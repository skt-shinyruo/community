package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.content.application.PostReadApplicationService;
import com.nowcoder.community.content.application.result.PostScanResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PostScanQueryApiAdapter implements PostScanQueryApi {

    private final PostReadApplicationService postReadApplicationService;

    public PostScanQueryApiAdapter(PostReadApplicationService postReadApplicationService) {
        this.postReadApplicationService = postReadApplicationService;
    }

    @Override
    public PostScanView scanPosts(UUID afterId, int limit) {
        return toView(postReadApplicationService.scanPosts(afterId, limit));
    }

    @Override
    public PostScanView.PostProjectionView getPostProjectionAllowDeleted(UUID postId) {
        return toView(postReadApplicationService.getPostProjectionAllowDeleted(postId));
    }

    private static PostScanView toView(PostScanResult result) {
        if (result == null) {
            return new PostScanView(List.of(), null, false);
        }
        return new PostScanView(
                result.items().stream().map(PostScanQueryApiAdapter::toView).toList(),
                result.nextAfterId(),
                result.hasMore()
        );
    }

    private static PostScanView.PostProjectionView toView(PostScanResult.PostProjectionResult result) {
        if (result == null) {
            return null;
        }
        return new PostScanView.PostProjectionView(
                result.postId(),
                result.userId(),
                result.categoryId(),
                result.tags(),
                result.title(),
                result.content(),
                result.type(),
                result.status(),
                result.createTime(),
                result.score()
        );
    }
}
