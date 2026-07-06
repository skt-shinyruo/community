package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.command.ProjectPostHotFeedCommand;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.service.PostHotnessDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class PostHotFeedProjectionApplicationService {

    private static final String RANK_VERSION = "hot-v1";

    private final PostContentRepository postContentRepository;
    private final LikeQueryPort likeQueryPort;
    private final PostFeedCache postFeedCache;
    private final PostSummaryCache postSummaryCache;
    private final PostDetailCache postDetailCache;
    private final PostCounterCache postCounterCache;
    private final PostHotnessDomainService postHotnessDomainService;

    public PostHotFeedProjectionApplicationService(
            PostContentRepository postContentRepository,
            LikeQueryPort likeQueryPort,
            PostFeedCache postFeedCache,
            PostSummaryCache postSummaryCache,
            PostDetailCache postDetailCache,
            PostCounterCache postCounterCache,
            PostHotnessDomainService postHotnessDomainService
    ) {
        this.postContentRepository = postContentRepository;
        this.likeQueryPort = likeQueryPort;
        this.postFeedCache = postFeedCache;
        this.postSummaryCache = postSummaryCache;
        this.postDetailCache = postDetailCache;
        this.postCounterCache = postCounterCache;
        this.postHotnessDomainService = postHotnessDomainService;
    }

    @Transactional
    public void project(ProjectPostHotFeedCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID postId = command.postId();
        if (postId == null) {
            return;
        }

        DiscussPost post = postContentRepository.getByIdAllowDeleted(postId);
        if (post == null || post.isDeleted()) {
            evictReadModels(postId);
            return;
        }

        UUID boardId = post.getCategoryId();
        long likeCount = likeQueryPort.countPostLikes(postId);
        double score = postHotnessDomainService.recomputeScore(post, likeCount, command.signalWeight());
        postContentRepository.updateScore(postId, score);
        postCounterCache.updateScore(postId, score);
        postFeedCache.remove(postId, null);
        postFeedCache.upsertGlobalHot(postId, score, RANK_VERSION);
        if (boardId != null) {
            postFeedCache.upsertBoardHot(boardId, postId, score, RANK_VERSION);
        }
        postSummaryCache.evictAll(List.of(postId));
        postDetailCache.evict(postId);
    }

    private void evictReadModels(UUID postId) {
        postFeedCache.remove(postId, null);
        postSummaryCache.evictAll(List.of(postId));
        postDetailCache.evict(postId);
    }
}
