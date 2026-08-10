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
import com.nowcoder.community.ops.application.command.RecordGovernanceAuditCommand;
import com.nowcoder.community.ops.domain.model.GovernanceAction;
import com.nowcoder.community.ops.domain.model.GovernanceResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
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

    public StatusResult getStatus(GetStatusCommand command) {
        GetStatusCommand c = validateStatus(command);
        HotFeedCacheStatusView view = hotFeedCacheGovernanceQueryApi.getStatus(c.scope(), c.boardId());
        String result = view.degraded() ? GovernanceResult.DEGRADED.name() : GovernanceResult.ACCEPTED.name();
        governanceMetrics.recordHotCacheGovernance(GovernanceAction.HOT_CACHE_STATUS.name(), result, c.scope());
        return new StatusResult(
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

    public DegradationSignalResult getDegradationSignal() {
        HotFeedDegradationSignalView view = hotFeedCacheGovernanceQueryApi.getDegradationSignal();
        return new DegradationSignalResult(view.degraded(), view.reason(), view.updatedAt());
    }

    public PrewarmResult prewarm(PrewarmCommand command) {
        PrewarmCommand c = validatePrewarm(command);
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
        return new PrewarmResult(
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

    public DegradationSignalResult updateDegradation(UpdateDegradationCommand command) {
        UpdateDegradationCommand c = validateDegradation(command);
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
        return new DegradationSignalResult(view.degraded(), view.reason(), view.updatedAt());
    }

    private GetStatusCommand validateStatus(GetStatusCommand command) {
        GetStatusCommand c = command == null
                ? new GetStatusCommand(SCOPE_GLOBAL, null)
                : command.normalized();
        validateScope(c.scope(), c.boardId());
        return c;
    }

    private PrewarmCommand validatePrewarm(PrewarmCommand command) {
        if (command == null || command.actorUserId() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "actorUserId is required");
        }
        PrewarmCommand c = command.normalized();
        validateScope(c.scope(), c.boardId());
        if (c.limit() < 1 || c.limit() > 500) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "limit must be between 1 and 500");
        }
        if (c.reason().isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "prewarm reason is required");
        }
        return c;
    }

    private UpdateDegradationCommand validateDegradation(UpdateDegradationCommand command) {
        if (command == null || command.actorUserId() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "actorUserId is required");
        }
        UpdateDegradationCommand c = command.normalized();
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

    public record GetStatusCommand(String scope, UUID boardId) {

        GetStatusCommand normalized() {
            return new GetStatusCommand(hasText(scope) ? scope.trim() : SCOPE_GLOBAL, boardId);
        }
    }

    public record PrewarmCommand(UUID actorUserId, String scope, UUID boardId, int limit, String reason) {

        PrewarmCommand normalized() {
            return new PrewarmCommand(
                    actorUserId,
                    hasText(scope) ? scope.trim() : SCOPE_GLOBAL,
                    boardId,
                    limit,
                    hasText(reason) ? reason.trim() : ""
            );
        }
    }

    public record UpdateDegradationCommand(UUID actorUserId, boolean degraded, String reason) {

        UpdateDegradationCommand normalized() {
            return new UpdateDegradationCommand(actorUserId, degraded, hasText(reason) ? reason.trim() : "");
        }
    }

    public record StatusResult(
            String scope,
            UUID boardId,
            String rankVersion,
            long itemCount,
            boolean summaryCacheAvailable,
            boolean degraded,
            String degradedReason,
            Instant lastPrewarmAt
    ) {
    }

    public record PrewarmResult(
            String scope,
            UUID boardId,
            int requestedCount,
            int loadedCount,
            int warmedCount,
            String rankVersion,
            boolean degraded,
            String degradedReason,
            Instant lastPrewarmAt
    ) {
    }

    public record DegradationSignalResult(boolean degraded, String reason, Instant updatedAt) {
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
