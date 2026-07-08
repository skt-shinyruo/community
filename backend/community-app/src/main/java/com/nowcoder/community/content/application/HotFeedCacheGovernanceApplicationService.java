package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.content.application.command.PrewarmHotFeedCacheCommand;
import com.nowcoder.community.content.application.command.UpdateHotFeedDegradationSignalCommand;
import com.nowcoder.community.content.application.result.HotFeedCachePrewarmResult;
import com.nowcoder.community.content.application.result.HotFeedCacheStatusResult;
import com.nowcoder.community.content.application.result.HotFeedDegradationSignalResult;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class HotFeedCacheGovernanceApplicationService {

    private static final String SCOPE_GLOBAL = "global";
    private static final String SCOPE_BOARD = "board";

    private final PostFeedCache postFeedCache;
    private final PostContentRepository postContentRepository;
    private final PostSummaryCache postSummaryCache;
    private final PostFeedSummaryLoader postFeedSummaryLoader;
    private final ContentFeedPolicyProperties policyProperties;

    public HotFeedCacheGovernanceApplicationService(
            PostFeedCache postFeedCache,
            PostContentRepository postContentRepository,
            PostSummaryCache postSummaryCache,
            PostFeedSummaryLoader postFeedSummaryLoader,
            ContentFeedPolicyProperties policyProperties
    ) {
        this.postFeedCache = Objects.requireNonNull(postFeedCache, "postFeedCache must not be null");
        this.postContentRepository = Objects.requireNonNull(postContentRepository, "postContentRepository must not be null");
        this.postSummaryCache = Objects.requireNonNull(postSummaryCache, "postSummaryCache must not be null");
        this.postFeedSummaryLoader = Objects.requireNonNull(postFeedSummaryLoader, "postFeedSummaryLoader must not be null");
        this.policyProperties = policyProperties == null ? new ContentFeedPolicyProperties() : policyProperties;
    }

    public HotFeedCacheStatusResult getStatus(String scope, UUID boardId) {
        String normalizedScope = validateScope(scope, boardId);
        HotFeedDegradationSignalResult signal = safeSignal();
        return new HotFeedCacheStatusResult(
                normalizedScope,
                boardId,
                postFeedCache.readRankVersion(),
                SCOPE_BOARD.equals(normalizedScope) ? postFeedCache.countBoardHot(boardId) : postFeedCache.countGlobalHot(),
                true,
                signal.degraded(),
                signal.reason(),
                postFeedCache.readLastPrewarmAt(normalizedScope, boardId)
        );
    }

    public HotFeedDegradationSignalResult getDegradationSignal() {
        return safeSignal();
    }

    public HotFeedCachePrewarmResult prewarm(PrewarmHotFeedCacheCommand command) {
        PrewarmHotFeedCacheCommand c = validatePrewarm(command);
        List<DiscussPost> posts = SCOPE_BOARD.equals(c.scope())
                ? postContentRepository.listPosts(0, c.limit(), PostContentRepository.ORDER_HOT, c.boardId(), null)
                : postContentRepository.listPosts(0, c.limit(), PostContentRepository.ORDER_HOT);
        String rankVersion = policyProperties.getHotRankVersion();
        postFeedCache.writeRankVersion(rankVersion);
        int warmed = 0;
        for (DiscussPost post : posts) {
            if (post == null || post.getId() == null) {
                continue;
            }
            if (SCOPE_BOARD.equals(c.scope())) {
                postFeedCache.upsertBoardHot(c.boardId(), post.getId(), post.getScore(), rankVersion);
            } else {
                postFeedCache.upsertGlobalHot(post.getId(), post.getScore(), rankVersion);
            }
            warmed++;
        }
        postSummaryCache.putAll(postFeedSummaryLoader.assembleSummaries(posts));
        Instant prewarmAt = Instant.now();
        postFeedCache.writeLastPrewarmAt(c.scope(), c.boardId(), prewarmAt);
        HotFeedDegradationSignalResult signal = safeSignal();
        return new HotFeedCachePrewarmResult(
                c.scope(),
                c.boardId(),
                c.limit(),
                posts.size(),
                warmed,
                rankVersion,
                signal.degraded(),
                signal.reason(),
                prewarmAt
        );
    }

    public HotFeedDegradationSignalResult updateDegradationSignal(UpdateHotFeedDegradationSignalCommand command) {
        if (command == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "degradation command is required");
        }
        UpdateHotFeedDegradationSignalCommand c = command.normalized();
        return postFeedCache.writeDegradationSignal(c.degraded(), c.degraded() ? c.reason() : "");
    }

    private PrewarmHotFeedCacheCommand validatePrewarm(PrewarmHotFeedCacheCommand command) {
        if (command == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "prewarm command is required");
        }
        PrewarmHotFeedCacheCommand c = command.normalized();
        validateScope(c.scope(), c.boardId());
        if (c.limit() < 1 || c.limit() > 500) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "limit must be between 1 and 500");
        }
        if (c.reason().isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "prewarm reason is required");
        }
        return c;
    }

    private String validateScope(String scope, UUID boardId) {
        String normalized = scope == null ? "" : scope.trim();
        if (normalized.isBlank()) {
            normalized = SCOPE_GLOBAL;
        }
        if (!SCOPE_GLOBAL.equals(normalized) && !SCOPE_BOARD.equals(normalized)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "scope must be global or board");
        }
        if (SCOPE_BOARD.equals(normalized) && boardId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "boardId is required for board scope");
        }
        return normalized;
    }

    private HotFeedDegradationSignalResult safeSignal() {
        HotFeedDegradationSignalResult signal = postFeedCache.readDegradationSignal();
        return signal == null ? new HotFeedDegradationSignalResult(false, "", null) : signal;
    }
}
