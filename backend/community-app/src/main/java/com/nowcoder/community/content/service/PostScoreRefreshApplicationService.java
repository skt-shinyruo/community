package com.nowcoder.community.content.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.like.LikeQueryService;
import com.nowcoder.community.content.score.PostScoreQueue;
import com.nowcoder.community.content.score.PostScoreUpdateService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class PostScoreRefreshApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PostScoreRefreshApplicationService.class);
    private static final LocalDateTime EPOCH = LocalDateTime.of(2014, 8, 1, 0, 0);

    private final PostScoreQueue scoreQueue;
    private final PostService postService;
    private final LikeQueryService likeQueryService;
    private final PostScoreUpdateService scoreUpdateService;
    private final MeterRegistry meterRegistry;

    public PostScoreRefreshApplicationService(
            PostScoreQueue scoreQueue,
            PostService postService,
            LikeQueryService likeQueryService,
            PostScoreUpdateService scoreUpdateService,
            MeterRegistry meterRegistry
    ) {
        this.scoreQueue = scoreQueue;
        this.postService = postService;
        this.likeQueryService = likeQueryService;
        this.scoreUpdateService = scoreUpdateService;
        this.meterRegistry = meterRegistry;
    }

    public void refreshBatch(int requestedBatchSize) {
        int batchSize = Math.max(1, Math.min(2000, requestedBatchSize));
        for (int i = 0; i < batchSize; i++) {
            UUID postId = scoreQueue.pop();
            if (postId == null) {
                return;
            }
            refreshSafely(postId);
        }
    }

    private void refreshSafely(UUID postId) {
        if (postId == null) {
            meterRegistry.counter("content_post_score_refresh_total", Tags.of("outcome", "drop_invalid")).increment();
            return;
        }
        try {
            refresh(postId);
            try {
                scoreQueue.onSuccess(postId);
            } catch (RuntimeException ignored) {
            }
            meterRegistry.counter("content_post_score_refresh_total", Tags.of("outcome", "success")).increment();
        } catch (BusinessException e) {
            ErrorCode errorCode = e.getErrorCode();
            if (errorCode != null && errorCode.getHttpStatus() == 404) {
                meterRegistry.counter("content_post_score_refresh_total", Tags.of("outcome", "drop_not_found")).increment();
                return;
            }
            meterRegistry.counter("content_post_score_refresh_total", Tags.of("outcome", "failed")).increment();
            reenqueue(postId, e);
        } catch (RuntimeException e) {
            meterRegistry.counter("content_post_score_refresh_total", Tags.of("outcome", "failed")).increment();
            reenqueue(postId, e);
        }
    }

    private void reenqueue(UUID postId, RuntimeException e) {
        try {
            scoreQueue.reenqueue(postId);
            meterRegistry.counter("content_post_score_refresh_total", Tags.of("outcome", "reenqueue")).increment();
        } catch (RuntimeException ex) {
            meterRegistry.counter("content_post_score_refresh_total", Tags.of("outcome", "reenqueue_failed")).increment();
            log.warn("[post-score] re-enqueue failed (postId={}): {}", postId, ex.toString());
        }
        log.warn("[post-score] refresh failed, re-enqueued (postId={}): {}", postId, e.toString());
    }

    private void refresh(UUID postId) {
        DiscussPost post = postService.getById(postId);

        boolean wonderful = post.getStatus() == 1;
        int commentCount = post.getCommentCount();
        long likeCount = likeQueryService.countPostLikes(postId);

        double w = (wonderful ? 75 : 0) + commentCount * 10.0 + likeCount * 2.0;
        double days = 0;
        if (post.getCreateTime() != null) {
            days = (post.getCreateTime().getTime() - EPOCH.toInstant(ZoneOffset.UTC).toEpochMilli()) / (1000.0 * 3600 * 24);
        }
        double score = Math.log10(Math.max(w, 1.0)) + days;

        scoreUpdateService.updateScore(postId, score);
    }
}
