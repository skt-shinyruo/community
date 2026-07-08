package com.nowcoder.community.ops.application;

import com.nowcoder.community.common.exception.BusinessException;
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
import com.nowcoder.community.ops.domain.model.GovernanceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HotCacheGovernanceApplicationServiceTest {

    private HotFeedCacheGovernanceQueryApi queryApi;
    private HotFeedCacheGovernanceActionApi actionApi;
    private GovernanceMetrics governanceMetrics;
    private GovernanceAuditPort auditPort;
    private HotCacheGovernanceApplicationService service;

    @BeforeEach
    void setUp() {
        queryApi = mock(HotFeedCacheGovernanceQueryApi.class);
        actionApi = mock(HotFeedCacheGovernanceActionApi.class);
        governanceMetrics = mock(GovernanceMetrics.class);
        auditPort = mock(GovernanceAuditPort.class);
        service = new HotCacheGovernanceApplicationService(queryApi, actionApi, governanceMetrics, auditPort);
    }

    @Test
    void statusShouldDelegateToContentQueryApi() {
        Instant prewarmAt = Instant.parse("2026-07-07T10:00:00Z");
        when(queryApi.getStatus("global", null)).thenReturn(new HotFeedCacheStatusView(
                "global",
                null,
                "hot-v2",
                20,
                true,
                false,
                "",
                prewarmAt
        ));

        var result = service.getStatus(new GetHotCacheStatusCommand("global", null));

        assertThat(result.scope()).isEqualTo("global");
        assertThat(result.itemCount()).isEqualTo(20);
        assertThat(result.lastPrewarmAt()).isEqualTo(prewarmAt);
        verify(queryApi).getStatus("global", null);
        verify(governanceMetrics).recordHotCacheGovernance("HOT_CACHE_STATUS", GovernanceResult.ACCEPTED.name(), "global");
    }

    @Test
    void prewarmShouldDelegateToContentActionApiAndAuditMetrics() {
        UUID actorId = uuid(99);
        UUID boardId = uuid(8);
        Instant prewarmAt = Instant.parse("2026-07-07T10:00:00Z");
        when(actionApi.prewarm(any())).thenReturn(new HotFeedCachePrewarmResultView(
                "board",
                boardId,
                20,
                18,
                18,
                "hot-v2",
                false,
                "",
                prewarmAt
        ));

        var result = service.prewarm(new PrewarmHotCacheCommand(actorId, "board", boardId, 20, "warm board"));

        assertThat(result.boardId()).isEqualTo(boardId);
        assertThat(result.warmedCount()).isEqualTo(18);
        ArgumentCaptor<HotFeedCachePrewarmRequest> requestCaptor =
                ArgumentCaptor.forClass(HotFeedCachePrewarmRequest.class);
        verify(actionApi).prewarm(requestCaptor.capture());
        HotFeedCachePrewarmRequest request = requestCaptor.getValue();
        assertAll(
                () -> assertEquals("board", request.scope()),
                () -> assertEquals(boardId, request.boardId()),
                () -> assertEquals(20, request.limit()),
                () -> assertEquals("warm board", request.reason())
        );
        verify(governanceMetrics).recordHotCacheGovernance("HOT_CACHE_PREWARM", GovernanceResult.ACCEPTED.name(), "board");
        verify(governanceMetrics).recordGovernanceAction("HOT_CACHE_PREWARM", GovernanceResult.ACCEPTED.name());
        verify(auditPort).record(any(RecordGovernanceAuditCommand.class));
    }

    @Test
    void degradationUpdateShouldDelegateToContentActionApiAndAuditMetrics() {
        UUID actorId = uuid(99);
        Instant updatedAt = Instant.parse("2026-07-07T10:00:00Z");
        when(actionApi.updateDegradationSignal(any())).thenReturn(new HotFeedDegradationSignalView(
                true,
                "redis maintenance",
                updatedAt
        ));

        var result = service.updateDegradation(new UpdateHotCacheDegradationCommand(actorId, true, "redis maintenance"));

        assertThat(result.degraded()).isTrue();
        assertThat(result.reason()).isEqualTo("redis maintenance");
        ArgumentCaptor<UpdateHotFeedDegradationSignalRequest> requestCaptor =
                ArgumentCaptor.forClass(UpdateHotFeedDegradationSignalRequest.class);
        verify(actionApi).updateDegradationSignal(requestCaptor.capture());
        assertThat(requestCaptor.getValue().degraded()).isTrue();
        assertThat(requestCaptor.getValue().reason()).isEqualTo("redis maintenance");
        verify(governanceMetrics).recordHotCacheGovernance("HOT_CACHE_DEGRADATION_SIGNAL", GovernanceResult.DEGRADED.name(), "global");
        verify(governanceMetrics).recordGovernanceAction("HOT_CACHE_DEGRADATION_SIGNAL", GovernanceResult.DEGRADED.name());
        verify(auditPort).record(any(RecordGovernanceAuditCommand.class));
    }

    @Test
    void boardScopeShouldRequireBoardId() {
        assertThatThrownBy(() -> service.getStatus(new GetHotCacheStatusCommand("board", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("boardId is required for board scope");
        assertThatThrownBy(() -> service.prewarm(new PrewarmHotCacheCommand(uuid(99), "board", null, 10, "warm")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("boardId is required for board scope");
    }

    @Test
    void mutatingRequestsShouldRequireReason() {
        assertThatThrownBy(() -> service.prewarm(new PrewarmHotCacheCommand(uuid(99), "global", null, 10, " ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("prewarm reason is required");
        assertThatThrownBy(() -> service.updateDegradation(new UpdateHotCacheDegradationCommand(uuid(99), true, " ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("degradation reason is required");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
