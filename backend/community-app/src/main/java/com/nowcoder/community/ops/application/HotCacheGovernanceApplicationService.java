package com.nowcoder.community.ops.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.content.api.action.HotFeedCacheGovernanceActionApi;
import com.nowcoder.community.content.api.model.HotFeedCachePrewarmRequest;
import com.nowcoder.community.content.api.model.HotFeedCachePrewarmResultView;
import com.nowcoder.community.content.api.model.HotFeedCacheStatusView;
import com.nowcoder.community.content.api.model.HotFeedDegradationSignalView;
import com.nowcoder.community.content.api.model.UpdateHotFeedDegradationSignalRequest;
import com.nowcoder.community.content.api.query.HotFeedCacheGovernanceQueryApi;
import com.nowcoder.community.ops.application.command.GetHotCacheStatusCommand;
import com.nowcoder.community.ops.application.command.PrewarmHotCacheCommand;
import com.nowcoder.community.ops.application.command.RecordGovernanceAuditCommand;
import com.nowcoder.community.ops.application.command.UpdateHotCacheDegradationCommand;
import com.nowcoder.community.ops.application.result.HotCacheDegradationSignalResult;
import com.nowcoder.community.ops.application.result.HotCachePrewarmResult;
import com.nowcoder.community.ops.application.result.HotCacheStatusResult;
import com.nowcoder.community.ops.domain.model.GovernanceAction;
import com.nowcoder.community.ops.domain.model.GovernanceResult;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class HotCacheGovernanceApplicationService {

    private static final String SCOPE_GLOBAL = "global";
    private static final String SCOPE_BOARD = "board";

    private final HotFeedCacheGovernanceQueryApi hotFeedCacheGovernanceQueryApi;
    private final HotFeedCacheGovernanceActionApi hotFeedCacheGovernanceActionApi;
    private final GovernanceMetrics governanceMetrics;
    private final GovernanceAuditPort governanceAuditPort;

    public HotCacheGovernanceApplicationService(
            HotFeedCacheGovernanceQueryApi hotFeedCacheGovernanceQueryApi,
            HotFeedCacheGovernanceActionApi hotFeedCacheGovernanceActionApi,
            GovernanceMetrics governanceMetrics,
            GovernanceAuditPort governanceAuditPort
    ) {
        this.hotFeedCacheGovernanceQueryApi = Objects.requireNonNull(hotFeedCacheGovernanceQueryApi, "hotFeedCacheGovernanceQueryApi must not be null");
        this.hotFeedCacheGovernanceActionApi = Objects.requireNonNull(hotFeedCacheGovernanceActionApi, "hotFeedCacheGovernanceActionApi must not be null");
        this.governanceMetrics = Objects.requireNonNull(governanceMetrics, "governanceMetrics must not be null");
        this.governanceAuditPort = Objects.requireNonNull(governanceAuditPort, "governanceAuditPort must not be null");
    }

    public HotCacheStatusResult getStatus(GetHotCacheStatusCommand command) {
        GetHotCacheStatusCommand c = validateStatus(command);
        HotFeedCacheStatusView view = hotFeedCacheGovernanceQueryApi.getStatus(c.scope(), c.boardId());
        String result = view.degraded() ? GovernanceResult.DEGRADED.name() : GovernanceResult.ACCEPTED.name();
        governanceMetrics.recordHotCacheGovernance(GovernanceAction.HOT_CACHE_STATUS.name(), result, c.scope());
        return new HotCacheStatusResult(
                view.scope(),
                view.boardId(),
                view.rankVersion(),
                view.itemCount(),
                view.summaryCacheAvailable(),
                view.degraded(),
                view.degradedReason(),
                view.lastPrewarmAt()
        );
    }

    public HotCacheDegradationSignalResult getDegradationSignal() {
        HotFeedDegradationSignalView view = hotFeedCacheGovernanceQueryApi.getDegradationSignal();
        return new HotCacheDegradationSignalResult(view.degraded(), view.reason(), view.updatedAt());
    }

    public HotCachePrewarmResult prewarm(PrewarmHotCacheCommand command) {
        PrewarmHotCacheCommand c = validatePrewarm(command);
        HotFeedCachePrewarmResultView view = hotFeedCacheGovernanceActionApi.prewarm(new HotFeedCachePrewarmRequest(
                c.scope(),
                c.boardId(),
                c.limit(),
                c.reason()
        ));
        String result = view.degraded() ? GovernanceResult.DEGRADED.name() : GovernanceResult.ACCEPTED.name();
        governanceMetrics.recordHotCacheGovernance(GovernanceAction.HOT_CACHE_PREWARM.name(), result, c.scope());
        governanceMetrics.recordGovernanceAction(GovernanceAction.HOT_CACHE_PREWARM.name(), result);
        governanceAuditPort.record(new RecordGovernanceAuditCommand(
                GovernanceAction.HOT_CACHE_PREWARM.name(),
                c.actorUserId(),
                "hot_cache",
                c.scope(),
                scope(c.scope(), c.boardId()),
                c.reason(),
                "{\"limit\":" + c.limit() + "}",
                result,
                "{\"loaded\":" + view.loadedCount() + ",\"warmed\":" + view.warmedCount() + "}",
                null
        ));
        return new HotCachePrewarmResult(
                view.scope(),
                view.boardId(),
                view.requestedCount(),
                view.loadedCount(),
                view.warmedCount(),
                view.rankVersion(),
                view.degraded(),
                view.degradedReason(),
                view.lastPrewarmAt()
        );
    }

    public HotCacheDegradationSignalResult updateDegradation(UpdateHotCacheDegradationCommand command) {
        UpdateHotCacheDegradationCommand c = validateDegradation(command);
        HotFeedDegradationSignalView view = hotFeedCacheGovernanceActionApi.updateDegradationSignal(
                new UpdateHotFeedDegradationSignalRequest(c.degraded(), c.reason())
        );
        String result = view.degraded() ? GovernanceResult.DEGRADED.name() : GovernanceResult.ACCEPTED.name();
        governanceMetrics.recordHotCacheGovernance(GovernanceAction.HOT_CACHE_DEGRADATION_SIGNAL.name(), result, SCOPE_GLOBAL);
        governanceMetrics.recordGovernanceAction(GovernanceAction.HOT_CACHE_DEGRADATION_SIGNAL.name(), result);
        governanceAuditPort.record(new RecordGovernanceAuditCommand(
                GovernanceAction.HOT_CACHE_DEGRADATION_SIGNAL.name(),
                c.actorUserId(),
                "hot_cache",
                "degradation",
                "scope=global",
                c.reason(),
                "{\"degraded\":" + c.degraded() + "}",
                result,
                "{\"degraded\":" + view.degraded() + ",\"reason\":\"" + safeJson(view.reason()) + "\"}",
                null
        ));
        return new HotCacheDegradationSignalResult(view.degraded(), view.reason(), view.updatedAt());
    }

    private GetHotCacheStatusCommand validateStatus(GetHotCacheStatusCommand command) {
        GetHotCacheStatusCommand c = command == null
                ? new GetHotCacheStatusCommand(SCOPE_GLOBAL, null)
                : command.normalized();
        validateScope(c.scope(), c.boardId());
        return c;
    }

    private PrewarmHotCacheCommand validatePrewarm(PrewarmHotCacheCommand command) {
        if (command == null || command.actorUserId() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "actorUserId is required");
        }
        PrewarmHotCacheCommand c = command.normalized();
        validateScope(c.scope(), c.boardId());
        if (c.limit() < 1 || c.limit() > 500) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "limit must be between 1 and 500");
        }
        if (c.reason().isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "prewarm reason is required");
        }
        return c;
    }

    private UpdateHotCacheDegradationCommand validateDegradation(UpdateHotCacheDegradationCommand command) {
        if (command == null || command.actorUserId() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "actorUserId is required");
        }
        UpdateHotCacheDegradationCommand c = command.normalized();
        if (c.reason().isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "degradation reason is required");
        }
        return c;
    }

    private void validateScope(String scope, UUID boardId) {
        if (!SCOPE_GLOBAL.equals(scope) && !SCOPE_BOARD.equals(scope)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "scope must be global or board");
        }
        if (SCOPE_BOARD.equals(scope) && boardId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "boardId is required for board scope");
        }
    }

    private String scope(String scope, UUID boardId) {
        return SCOPE_BOARD.equals(scope) ? "scope=board,boardId=" + boardId : "scope=global";
    }

    private static String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
