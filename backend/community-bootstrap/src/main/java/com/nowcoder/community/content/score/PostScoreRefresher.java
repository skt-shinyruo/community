package com.nowcoder.community.content.score;

import com.nowcoder.community.contracts.api.ErrorCode;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.like.LikeQueryService;
import com.nowcoder.community.content.service.PostService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class PostScoreRefresher {

    private static final Logger log = LoggerFactory.getLogger(PostScoreRefresher.class);
    private static final LocalDateTime EPOCH = LocalDateTime.of(2014, 8, 1, 0, 0);

    private final PostScoreQueue scoreQueue;
    private final PostService postService;
    private final LikeQueryService likeQueryService;
    private final PostScoreCommandService scoreCommandService;
    private final MeterRegistry meterRegistry;

    private final boolean enabled;
    private final int batchSize;

    public PostScoreRefresher(
            PostScoreQueue scoreQueue,
            PostService postService,
            LikeQueryService likeQueryService,
            PostScoreCommandService scoreCommandService,
            MeterRegistry meterRegistry,
            @Value("${content.score.refresh.enabled:true}") boolean enabled,
            @Value("${content.score.refresh.batch-size:200}") int batchSize
    ) {
        this.scoreQueue = scoreQueue;
        this.postService = postService;
        this.likeQueryService = likeQueryService;
        this.scoreCommandService = scoreCommandService;
        this.meterRegistry = meterRegistry;
        this.enabled = enabled;
        this.batchSize = Math.max(1, Math.min(2000, batchSize));
    }

    @Scheduled(fixedDelayString = "${content.score.refresh.delay-ms:30000}")
    public void refreshBatch() {
        if (!enabled) {
            return;
        }
        for (int i = 0; i < batchSize; i++) {
            Integer postId = scoreQueue.pop();
            if (postId == null) {
                return;
            }
            refreshSafely(postId);
        }
    }

    void refreshSafely(int postId) {
        int pid = Math.max(0, postId);
        if (pid <= 0) {
            meterRegistry.counter("content_post_score_refresh_total", Tags.of("outcome", "drop_invalid")).increment();
            return;
        }
        try {
            refresh(pid);
            try {
                scoreQueue.onSuccess(pid);
            } catch (RuntimeException ignored) {
            }
            meterRegistry.counter("content_post_score_refresh_total", Tags.of("outcome", "success")).increment();
        } catch (BusinessException e) {
            ErrorCode errorCode = e.getErrorCode();
            if (errorCode != null && errorCode.getHttpStatus() == 404) {
                // 资源已不存在：认为无需再刷新，直接丢弃（避免死循环）
                meterRegistry.counter("content_post_score_refresh_total", Tags.of("outcome", "drop_not_found")).increment();
                return;
            }
            meterRegistry.counter("content_post_score_refresh_total", Tags.of("outcome", "failed")).increment();
            reenqueue(pid, e);
        } catch (RuntimeException e) {
            meterRegistry.counter("content_post_score_refresh_total", Tags.of("outcome", "failed")).increment();
            reenqueue(pid, e);
        }
    }

    private void reenqueue(int postId, RuntimeException e) {
        try {
            scoreQueue.reenqueue(postId);
            meterRegistry.counter("content_post_score_refresh_total", Tags.of("outcome", "reenqueue")).increment();
        } catch (RuntimeException ex) {
            meterRegistry.counter("content_post_score_refresh_total", Tags.of("outcome", "reenqueue_failed")).increment();
            log.warn("[post-score] re-enqueue failed (postId={}): {}", postId, ex.toString());
        }
        log.warn("[post-score] refresh failed, re-enqueued (postId={}): {}", postId, e.toString());
    }

    void refresh(int postId) {
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

        scoreCommandService.updateScore(postId, score);
    }
}
