package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.content.application.result.HotFeedDegradationSignalResult;
import com.nowcoder.community.content.api.action.HotFeedCacheGovernanceActionApi;
import com.nowcoder.community.content.api.model.HotFeedCachePrewarmRequest;
import com.nowcoder.community.content.api.model.HotFeedCachePrewarmResultView;
import com.nowcoder.community.content.api.model.HotFeedCacheStatusView;
import com.nowcoder.community.content.api.model.HotFeedDegradationSignalView;
import com.nowcoder.community.content.api.model.UpdateHotFeedDegradationSignalRequest;
import com.nowcoder.community.content.api.query.HotFeedCacheGovernanceQueryApi;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class HotFeedCacheGovernanceApplicationService
        implements HotFeedCacheGovernanceQueryApi, HotFeedCacheGovernanceActionApi {

    private static final String SCOPE_GLOBAL = "global";
    private static final String SCOPE_BOARD = "board";

    private final PostFeedCache postFeedCache;
    private final PostContentRepository postContentRepository;
    private final PostSummaryCache postSummaryCache;
    private final PostFeedSummaryLoader postFeedSummaryLoader;
    private final ContentFeedPolicyProperties policyProperties;
    private final Clock clock;

    public HotFeedCacheGovernanceApplicationService(
            PostFeedCache postFeedCache,
            PostContentRepository postContentRepository,
            PostSummaryCache postSummaryCache,
            PostFeedSummaryLoader postFeedSummaryLoader,
            ContentFeedPolicyProperties policyProperties,
            Clock clock
    ) {
        this.postFeedCache = Objects.requireNonNull(postFeedCache, "postFeedCache must not be null");
        this.postContentRepository = Objects.requireNonNull(postContentRepository, "postContentRepository must not be null");
        this.postSummaryCache = Objects.requireNonNull(postSummaryCache, "postSummaryCache must not be null");
        this.postFeedSummaryLoader = Objects.requireNonNull(postFeedSummaryLoader, "postFeedSummaryLoader must not be null");
        this.policyProperties = Objects.requireNonNull(policyProperties, "policyProperties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public HotFeedCacheStatusView getStatus(String scope, UUID boardId) {
        String normalizedScope = validateScope(scope, boardId);
        HotFeedDegradationSignalResult signal = safeSignal();
        return new HotFeedCacheStatusView(
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

    @Override
    public HotFeedDegradationSignalView getDegradationSignal() {
        return toView(safeSignal());
    }

    @Override
    public HotFeedCachePrewarmResultView prewarm(HotFeedCachePrewarmRequest request) {
        HotFeedCachePrewarmRequest c = validatePrewarm(request);
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
                postFeedCache.upsertBoardHot(
                        c.boardId(),
                        projectionEntry(post, post.getScore()),
                        rankVersion,
                        post.getAggregateVersion(),
                        post.getScoreVersion()
                );
            } else {
                postFeedCache.upsertGlobalHot(
                        projectionEntry(post, post.getScore()),
                        rankVersion,
                        post.getAggregateVersion(),
                        post.getScoreVersion()
                );
            }
            warmed++;
        }
        var summaries = postFeedSummaryLoader.assembleSummaries(posts);
        postFeedSummaryLoader.cacheSummaries(posts, summaries);
        Instant prewarmAt = clock.instant();
        postFeedCache.writeLastPrewarmAt(c.scope(), c.boardId(), prewarmAt);
        HotFeedDegradationSignalResult signal = safeSignal();
        return new HotFeedCachePrewarmResultView(
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

    private static PostFeedCache.HotProjectionEntry projectionEntry(DiscussPost post, double score) {
        return new PostFeedCache.HotProjectionEntry(
                post.getId(), post.getType(), score, post.getCreateTime());
    }

    @Override
    public HotFeedDegradationSignalView updateDegradationSignal(UpdateHotFeedDegradationSignalRequest request) {
        if (request == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "degradation command is required");
        }
        String reason = trim(request.reason());
        return toView(postFeedCache.writeDegradationSignal(request.degraded(), request.degraded() ? reason : ""));
    }

    private HotFeedCachePrewarmRequest validatePrewarm(HotFeedCachePrewarmRequest request) {
        if (request == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "prewarm command is required");
        }
        HotFeedCachePrewarmRequest c = new HotFeedCachePrewarmRequest(
                trim(request.scope()),
                request.boardId(),
                request.limit(),
                trim(request.reason())
        );
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

    private HotFeedDegradationSignalView toView(HotFeedDegradationSignalResult signal) {
        return new HotFeedDegradationSignalView(signal.degraded(), signal.reason(), signal.updatedAt());
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
