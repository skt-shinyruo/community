package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.command.ProjectPostHotFeedCommand;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.service.PostHotnessDomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final HotFeedProjectionGuard projectionGuard;

    @Autowired
    public PostHotFeedProjectionApplicationService(
            PostContentRepository postContentRepository,
            LikeQueryPort likeQueryPort,
            PostFeedCache postFeedCache,
            PostSummaryCache postSummaryCache,
            PostDetailCache postDetailCache,
            PostCounterCache postCounterCache,
            PostHotnessDomainService postHotnessDomainService,
            ContentFeedPolicyProperties policyProperties,
            HotFeedProjectionGuard projectionGuard
    ) {
        this.postContentRepository = postContentRepository;
        this.likeQueryPort = likeQueryPort;
        this.postFeedCache = postFeedCache;
        this.postSummaryCache = postSummaryCache;
        this.postDetailCache = postDetailCache;
        this.postCounterCache = postCounterCache;
        this.postHotnessDomainService = postHotnessDomainService;
        this.policyProperties = policyProperties == null ? new ContentFeedPolicyProperties() : policyProperties;
        this.projectionGuard = projectionGuard == null ? AllowAllHotFeedProjectionGuard.INSTANCE : projectionGuard;
    }

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
        this(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties,
                AllowAllHotFeedProjectionGuard.INSTANCE
        );
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
        if (!StringUtils.hasText(command.sourceEventId()) || command.sourceVersion() <= 0L) {
            return;
        }
        HotFeedProjectionGuard.ProjectionAttempt attempt = projectionGuard.tryBegin(
                postId,
                command.sourceEventId().trim(),
                command.sourceVersion()
        );
        if (!attempt.accepted()) {
            return;
        }

        boolean committed = false;
        try {
            DiscussPost post = postContentRepository.getByIdAllowDeleted(postId);
            String rankVersion = policyProperties.getHotRankVersion();
            if (!projectionGuard.isCurrent(attempt)) {
                return;
            }
            if (post == null || post.isDeleted() || post.getStatus() != 0) {
                postFeedCache.writeRankVersion(rankVersion);
                evictReadModels(postId);
                commitAfterTransaction(attempt);
                committed = true;
                return;
            }

            UUID boardId = post.getCategoryId();
            long likeCount = likeQueryPort.countPostLikes(postId);
            double score = postHotnessDomainService.recomputeScore(post, likeCount, command.signalWeight());
            if (!projectionGuard.isCurrent(attempt)) {
                return;
            }
            postContentRepository.updateScore(postId, score);
            postCounterCache.updateScore(postId, score);
            postFeedCache.writeRankVersion(rankVersion);
            postFeedCache.remove(postId, null);
            postFeedCache.upsertGlobalHot(postId, score, rankVersion);
            if (boardId != null) {
                postFeedCache.upsertBoardHot(boardId, postId, score, rankVersion);
            }
            postSummaryCache.evictAll(List.of(postId));
            postDetailCache.evict(postId);
            commitAfterTransaction(attempt);
            committed = true;
        } finally {
            if (!committed) {
                projectionGuard.abort(attempt);
            }
        }
    }

    private void evictReadModels(UUID postId) {
        postFeedCache.remove(postId, null);
        postSummaryCache.evictAll(List.of(postId));
        postDetailCache.evict(postId);
    }

    private void commitAfterTransaction(HotFeedProjectionGuard.ProjectionAttempt attempt) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            projectionGuard.commit(attempt);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                projectionGuard.commit(attempt);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    projectionGuard.abort(attempt);
                }
            }
        });
    }

    private enum AllowAllHotFeedProjectionGuard implements HotFeedProjectionGuard {
        INSTANCE;

        @Override
        public ProjectionAttempt tryBegin(UUID postId, String sourceEventId, long sourceVersion) {
            return ProjectionAttempt.accepted(postId, sourceEventId, sourceVersion, "allow-all");
        }

        @Override
        public boolean isCurrent(ProjectionAttempt attempt) {
            return true;
        }

        @Override
        public void commit(ProjectionAttempt attempt) {
        }

        @Override
        public void abort(ProjectionAttempt attempt) {
        }
    }
}
