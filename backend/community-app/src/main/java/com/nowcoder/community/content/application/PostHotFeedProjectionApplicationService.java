package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.command.ProjectPostHotFeedCommand;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.service.PostHotnessDomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class PostHotFeedProjectionApplicationService {

    private final PostContentRepository postContentRepository;
    private final LikeQueryPort likeQueryPort;
    private final PostFeedCache postFeedCache;
    private final PostSummaryCache postSummaryCache;
    private final PostDetailCache postDetailCache;
    private final PostCounterCache postCounterCache;
    private final PostHotnessDomainService postHotnessDomainService;
    private final ContentFeedPolicyProperties policyProperties;

    @Autowired
    public PostHotFeedProjectionApplicationService(
            PostContentRepository postContentRepository,
            LikeQueryPort likeQueryPort,
            PostFeedCache postFeedCache,
            PostSummaryCache postSummaryCache,
            PostDetailCache postDetailCache,
            PostCounterCache postCounterCache,
            PostHotnessDomainService postHotnessDomainService,
            ContentFeedPolicyProperties policyProperties
    ) {
        this.postContentRepository = postContentRepository;
        this.likeQueryPort = likeQueryPort;
        this.postFeedCache = postFeedCache;
        this.postSummaryCache = postSummaryCache;
        this.postDetailCache = postDetailCache;
        this.postCounterCache = postCounterCache;
        this.postHotnessDomainService = postHotnessDomainService;
        this.policyProperties = policyProperties == null ? new ContentFeedPolicyProperties() : policyProperties;
    }

    public PostHotFeedProjectionApplicationService(
            PostContentRepository postContentRepository,
            LikeQueryPort likeQueryPort,
            PostFeedCache postFeedCache,
            PostSummaryCache postSummaryCache,
            PostDetailCache postDetailCache,
            PostCounterCache postCounterCache,
            PostHotnessDomainService postHotnessDomainService
    ) {
        this(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                new ContentFeedPolicyProperties()
        );
    }

    @Transactional
    public void project(ProjectPostHotFeedCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID postId = command.postId();
        if (postId == null) {
            return;
        }

        DiscussPost post = postContentRepository.getByIdAllowDeleted(postId);
        String rankVersion = policyProperties.getHotRankVersion();
        postFeedCache.writeRankVersion(rankVersion);
        if (post == null || post.isDeleted() || post.getStatus() != 0) {
            evictReadModels(postId);
            return;
        }

        UUID boardId = post.getCategoryId();
        long likeCount = likeQueryPort.countPostLikes(postId);
        double score = postHotnessDomainService.recomputeScore(post, likeCount, command.signalWeight());
        postContentRepository.updateScore(postId, score);
        postCounterCache.updateScore(postId, score);
        postFeedCache.remove(postId, null);
        postFeedCache.upsertGlobalHot(postId, score, rankVersion);
        if (boardId != null) {
            postFeedCache.upsertBoardHot(boardId, postId, score, rankVersion);
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
