package com.nowcoder.community.content.score;

import com.nowcoder.community.common.event.payload.PostPayload;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.event.ContentEventPublisher;
import com.nowcoder.community.content.like.LikeQueryService;
import com.nowcoder.community.content.service.PostService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class PostScoreRefresher {

    private static final LocalDateTime EPOCH = LocalDateTime.of(2014, 8, 1, 0, 0);

    private final PostScoreQueue scoreQueue;
    private final PostService postService;
    private final LikeQueryService likeQueryService;
    private final ContentEventPublisher eventPublisher;

    private final boolean enabled;
    private final int batchSize;

    public PostScoreRefresher(
            PostScoreQueue scoreQueue,
            PostService postService,
            LikeQueryService likeQueryService,
            ContentEventPublisher eventPublisher,
            @Value("${content.score.refresh.enabled:true}") boolean enabled,
            @Value("${content.score.refresh.batch-size:200}") int batchSize
    ) {
        this.scoreQueue = scoreQueue;
        this.postService = postService;
        this.likeQueryService = likeQueryService;
        this.eventPublisher = eventPublisher;
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
            refresh(postId);
        }
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

        postService.updateScore(postId, score);

        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setUserId(post.getUserId());
        payload.setTitle(post.getTitle());
        payload.setContent(post.getContent());
        payload.setType(post.getType());
        payload.setStatus(post.getStatus());
        payload.setScore(score);
        payload.setCreateTime(post.getCreateTime() == null ? null : post.getCreateTime().toInstant());
        eventPublisher.publishPostUpdated(payload);
    }
}
