package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.action.HotFeedCacheGovernanceActionApi;
import com.nowcoder.community.content.api.model.HotFeedCachePrewarmRequest;
import com.nowcoder.community.content.api.model.HotFeedCachePrewarmResultView;
import com.nowcoder.community.content.api.model.HotFeedCacheStatusView;
import com.nowcoder.community.content.api.model.HotFeedDegradationSignalView;
import com.nowcoder.community.content.api.model.UpdateHotFeedDegradationSignalRequest;
import com.nowcoder.community.content.api.query.HotFeedCacheGovernanceQueryApi;
import com.nowcoder.community.content.application.HotFeedCacheGovernanceApplicationService;
import com.nowcoder.community.content.application.command.PrewarmHotFeedCacheCommand;
import com.nowcoder.community.content.application.command.UpdateHotFeedDegradationSignalCommand;
import com.nowcoder.community.content.application.result.HotFeedCachePrewarmResult;
import com.nowcoder.community.content.application.result.HotFeedCacheStatusResult;
import com.nowcoder.community.content.application.result.HotFeedDegradationSignalResult;
import org.springframework.stereotype.Component;

@Component
public class HotFeedCacheGovernanceApiAdapter implements HotFeedCacheGovernanceQueryApi, HotFeedCacheGovernanceActionApi {

    private final HotFeedCacheGovernanceApplicationService hotFeedCacheGovernanceApplicationService;

    public HotFeedCacheGovernanceApiAdapter(HotFeedCacheGovernanceApplicationService hotFeedCacheGovernanceApplicationService) {
        this.hotFeedCacheGovernanceApplicationService = hotFeedCacheGovernanceApplicationService;
    }

    @Override
    public HotFeedCacheStatusView getStatus(String scope, java.util.UUID boardId) {
        return toStatusView(hotFeedCacheGovernanceApplicationService.getStatus(scope, boardId));
    }

    @Override
    public HotFeedDegradationSignalView getDegradationSignal() {
        return toSignalView(hotFeedCacheGovernanceApplicationService.getDegradationSignal());
    }

    @Override
    public HotFeedCachePrewarmResultView prewarm(HotFeedCachePrewarmRequest request) {
        HotFeedCachePrewarmRequest r = request == null
                ? new HotFeedCachePrewarmRequest(null, null, 0, null)
                : request;
        return toPrewarmView(hotFeedCacheGovernanceApplicationService.prewarm(new PrewarmHotFeedCacheCommand(
                r.scope(),
                r.boardId(),
                r.limit(),
                r.reason()
        )));
    }

    @Override
    public HotFeedDegradationSignalView updateDegradationSignal(UpdateHotFeedDegradationSignalRequest request) {
        UpdateHotFeedDegradationSignalRequest r = request == null
                ? new UpdateHotFeedDegradationSignalRequest(false, null)
                : request;
        return toSignalView(hotFeedCacheGovernanceApplicationService.updateDegradationSignal(
                new UpdateHotFeedDegradationSignalCommand(r.degraded(), r.reason())
        ));
    }

    private HotFeedCacheStatusView toStatusView(HotFeedCacheStatusResult result) {
        return new HotFeedCacheStatusView(
                result.scope(),
                result.boardId(),
                result.rankVersion(),
                result.itemCount(),
                result.summaryCacheAvailable(),
                result.degraded(),
                result.degradedReason(),
                result.lastPrewarmAt()
        );
    }

    private HotFeedCachePrewarmResultView toPrewarmView(HotFeedCachePrewarmResult result) {
        return new HotFeedCachePrewarmResultView(
                result.scope(),
                result.boardId(),
                result.requestedCount(),
                result.loadedCount(),
                result.warmedCount(),
                result.rankVersion(),
                result.degraded(),
                result.degradedReason(),
                result.lastPrewarmAt()
        );
    }

    private HotFeedDegradationSignalView toSignalView(HotFeedDegradationSignalResult result) {
        return new HotFeedDegradationSignalView(result.degraded(), result.reason(), result.updatedAt());
    }
}
