package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.command.ProjectPostHotFeedCommand;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.service.PostHotnessDomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
    private final HotFeedProjectionCompletion projectionCompletion;
    private final PostHotFeedProjectionTransactionOperations transactionOperations;

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
            HotFeedProjectionGuard projectionGuard,
            PostHotFeedProjectionTransactionOperations transactionOperations,
            HotFeedProjectionCompletion projectionCompletion
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
        this.transactionOperations = Objects.requireNonNull(transactionOperations, "transactionOperations must not be null");
        this.projectionCompletion = Objects.requireNonNull(projectionCompletion, "projectionCompletion must not be null");
    }

    public PostHotFeedProjectionApplicationService(
            PostContentRepository postContentRepository,
            LikeQueryPort likeQueryPort,
            PostFeedCache postFeedCache,
            PostSummaryCache postSummaryCache,
            PostDetailCache postDetailCache,
            PostCounterCache postCounterCache,
            PostHotnessDomainService postHotnessDomainService,
            ContentFeedPolicyProperties policyProperties,
            HotFeedProjectionGuard projectionGuard,
            HotFeedProjectionCompletion projectionCompletion
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
                projectionGuard,
                new PostHotFeedProjectionTransactionOperations(postContentRepository),
                projectionCompletion
        );
    }

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
        this(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties,
                projectionGuard,
                ImmediateHotFeedProjectionCompletion.INSTANCE
        );
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

    public void project(ProjectPostHotFeedCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID postId = command.postId();
        if (postId == null) {
            return;
        }
        if (!StringUtils.hasText(command.sourceEventId())
                || command.sourceVersion() <= 0L
                || command.sourceVersionLane() == null) {
            return;
        }
        HotFeedProjectionGuard.ProjectionAttempt attempt = projectionGuard.tryBegin(
                postId,
                command.sourceEventId().trim(),
                command.sourceVersion(),
                command.sourceVersionLane(),
                command.terminalDeletion()
        );
        if (!attempt.accepted()) {
            return;
        }

        boolean committed = false;
        try {
            String rankVersion = policyProperties.getHotRankVersion();
            if (command.terminalDeletion()) {
                if (!projectionGuard.isCurrent(attempt)) {
                    return;
                }
                postFeedCache.writeRankVersion(rankVersion);
                long aggregateVersion = command.sourceVersionLane() == PostProjectionVersionLane.POST
                        ? command.sourceVersion()
                        : 0L;
                terminallyEvictReadModels(postId, command.boardId(), aggregateVersion);
                commitAfterTransaction(attempt);
                committed = true;
                return;
            }

            DiscussPost post = postContentRepository.getByIdAllowDeleted(postId);
            if (!projectionGuard.isCurrent(attempt)) {
                return;
            }
            if (post == null) {
                postFeedCache.writeRankVersion(rankVersion);
                long aggregateVersion = command.sourceVersionLane() == PostProjectionVersionLane.POST
                        ? command.sourceVersion()
                        : 0L;
                terminallyEvictReadModels(postId, command.boardId(), aggregateVersion);
                commitAfterTransaction(attempt);
                committed = true;
                return;
            }
            if (post.isDeleted()) {
                postFeedCache.writeRankVersion(rankVersion);
                terminallyEvictReadModels(postId, post.getCategoryId(), post.getAggregateVersion());
                commitAfterTransaction(attempt);
                committed = true;
                return;
            }

            UUID boardId = post.getCategoryId();
            long aggregateVersion = post.getAggregateVersion();
            long likeCount = likeQueryPort.countPostLikes(postId);
            double score = postHotnessDomainService.recomputeScore(post, likeCount);
            if (!projectionGuard.isCurrent(attempt)) {
                return;
            }
            long scoreVersion = transactionOperations.updateScore(postId, score, aggregateVersion);
            if (!projectionGuard.isCurrent(attempt)) {
                return;
            }
            postCounterCache.updateScore(postId, score);
            postFeedCache.writeRankVersion(rankVersion);
            postFeedCache.remove(postId, null, aggregateVersion);
            postFeedCache.upsertGlobalHot(postId, score, rankVersion, aggregateVersion, scoreVersion);
            if (boardId != null) {
                postFeedCache.upsertBoardHot(boardId, postId, score, rankVersion, aggregateVersion, scoreVersion);
            }
            postSummaryCache.evictAll(List.of(postId), aggregateVersion, scoreVersion);
            postDetailCache.evict(postId, aggregateVersion);
            commitAfterTransaction(attempt);
            committed = true;
        } finally {
            if (!committed) {
                projectionGuard.abort(attempt);
            }
        }
    }

    private void terminallyEvictReadModels(UUID postId, UUID boardId, long aggregateVersion) {
        postFeedCache.terminalRemove(postId, boardId, aggregateVersion);
        postSummaryCache.terminalEvict(postId, aggregateVersion);
        postDetailCache.terminalEvict(postId, aggregateVersion);
    }

    private void commitAfterTransaction(HotFeedProjectionGuard.ProjectionAttempt attempt) {
        projectionCompletion.afterTransaction(
                () -> projectionGuard.commit(attempt),
                () -> projectionGuard.abort(attempt)
        );
    }

    private enum ImmediateHotFeedProjectionCompletion implements HotFeedProjectionCompletion {
        INSTANCE;

        @Override
        public void afterTransaction(Runnable committedAction, Runnable rolledBackAction) {
            committedAction.run();
        }
    }

    private enum AllowAllHotFeedProjectionGuard implements HotFeedProjectionGuard {
        INSTANCE;

        @Override
        public ProjectionAttempt tryBegin(
                UUID postId,
                String sourceEventId,
                long sourceVersion,
                PostProjectionVersionLane sourceVersionLane,
                boolean terminalDeletion
        ) {
            return ProjectionAttempt.accepted(
                    postId,
                    sourceEventId,
                    sourceVersion,
                    sourceVersionLane,
                    terminalDeletion,
                    "allow-all"
            );
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
