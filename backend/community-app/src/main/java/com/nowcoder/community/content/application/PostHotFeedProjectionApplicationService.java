package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.service.PostHotnessDomainService;
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
        this.postContentRepository = Objects.requireNonNull(
                postContentRepository, "postContentRepository must not be null");
        this.likeQueryPort = Objects.requireNonNull(likeQueryPort, "likeQueryPort must not be null");
        this.postFeedCache = Objects.requireNonNull(postFeedCache, "postFeedCache must not be null");
        this.postSummaryCache = Objects.requireNonNull(postSummaryCache, "postSummaryCache must not be null");
        this.postDetailCache = Objects.requireNonNull(postDetailCache, "postDetailCache must not be null");
        this.postCounterCache = Objects.requireNonNull(postCounterCache, "postCounterCache must not be null");
        this.postHotnessDomainService = Objects.requireNonNull(
                postHotnessDomainService, "postHotnessDomainService must not be null");
        this.policyProperties = Objects.requireNonNull(policyProperties, "policyProperties must not be null");
        this.projectionGuard = Objects.requireNonNull(projectionGuard, "projectionGuard must not be null");
        this.transactionOperations = Objects.requireNonNull(transactionOperations, "transactionOperations must not be null");
        this.projectionCompletion = Objects.requireNonNull(projectionCompletion, "projectionCompletion must not be null");
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
            postCounterCache.markDirty(postId);
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

    public record ProjectPostHotFeedCommand(
            UUID postId,
            UUID boardId,
            String sourceEventId,
            long sourceVersion,
            PostProjectionVersionLane sourceVersionLane,
            boolean terminalDeletion
    ) {
    }
}
