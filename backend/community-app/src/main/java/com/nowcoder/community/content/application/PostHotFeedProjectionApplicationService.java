package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.command.ProjectPostHotFeedCommand;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.service.PostHotnessDomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
    private final HotFeedProjectionCompletion projectionCompletion;

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
                command.sourceVersion(),
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
                terminallyEvictReadModels(postId, command.boardId());
                commitAfterTransaction(attempt);
                committed = true;
                return;
            }

            DiscussPost post = postContentRepository.getByIdAllowDeleted(postId);
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

    private void terminallyEvictReadModels(UUID postId, UUID boardId) {
        postFeedCache.terminalRemove(postId, boardId);
        postSummaryCache.terminalEvict(postId);
        postDetailCache.terminalEvict(postId);
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
                boolean terminalDeletion
        ) {
            return ProjectionAttempt.accepted(postId, sourceEventId, sourceVersion, terminalDeletion, "allow-all");
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
