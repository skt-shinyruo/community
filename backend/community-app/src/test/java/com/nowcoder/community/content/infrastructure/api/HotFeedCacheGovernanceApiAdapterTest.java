package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.model.HotFeedCachePrewarmRequest;
import com.nowcoder.community.content.api.model.HotFeedCachePrewarmResultView;
import com.nowcoder.community.content.api.model.HotFeedCacheStatusView;
import com.nowcoder.community.content.api.model.HotFeedDegradationSignalView;
import com.nowcoder.community.content.api.model.UpdateHotFeedDegradationSignalRequest;
import com.nowcoder.community.content.application.HotFeedCacheGovernanceApplicationService;
import com.nowcoder.community.content.application.command.PrewarmHotFeedCacheCommand;
import com.nowcoder.community.content.application.command.UpdateHotFeedDegradationSignalCommand;
import com.nowcoder.community.content.application.result.HotFeedCachePrewarmResult;
import com.nowcoder.community.content.application.result.HotFeedCacheStatusResult;
import com.nowcoder.community.content.application.result.HotFeedDegradationSignalResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HotFeedCacheGovernanceApiAdapterTest {

    @Test
    void queryMethodsShouldDelegateToApplicationServiceAndMapViews() {
        HotFeedCacheGovernanceApplicationService service = mock(HotFeedCacheGovernanceApplicationService.class);
        UUID boardId = uuid(8);
        Instant prewarmAt = Instant.parse("2026-07-07T10:00:00Z");
        when(service.getStatus("board", boardId)).thenReturn(new HotFeedCacheStatusResult(
                "board",
                boardId,
                "hot-v2",
                12,
                true,
                false,
                "",
                prewarmAt
        ));
        when(service.getDegradationSignal()).thenReturn(new HotFeedDegradationSignalResult(true, "redis down", prewarmAt));
        HotFeedCacheGovernanceApiAdapter adapter = new HotFeedCacheGovernanceApiAdapter(service);

        HotFeedCacheStatusView status = adapter.getStatus("board", boardId);
        HotFeedDegradationSignalView signal = adapter.getDegradationSignal();

        assertThat(status.boardId()).isEqualTo(boardId);
        assertThat(status.itemCount()).isEqualTo(12);
        assertThat(status.lastPrewarmAt()).isEqualTo(prewarmAt);
        assertThat(signal.degraded()).isTrue();
        assertThat(signal.reason()).isEqualTo("redis down");
        verify(service).getStatus("board", boardId);
        verify(service).getDegradationSignal();
    }

    @Test
    void actionMethodsShouldDelegateToApplicationServiceAndMapViews() {
        HotFeedCacheGovernanceApplicationService service = mock(HotFeedCacheGovernanceApplicationService.class);
        UUID boardId = uuid(8);
        Instant prewarmAt = Instant.parse("2026-07-07T10:00:00Z");
        when(service.prewarm(org.mockito.ArgumentMatchers.any())).thenReturn(new HotFeedCachePrewarmResult(
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
        when(service.updateDegradationSignal(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new HotFeedDegradationSignalResult(false, "", prewarmAt));
        HotFeedCacheGovernanceApiAdapter adapter = new HotFeedCacheGovernanceApiAdapter(service);

        HotFeedCachePrewarmResultView prewarm = adapter.prewarm(new HotFeedCachePrewarmRequest(
                "board",
                boardId,
                20,
                "warm board"
        ));
        HotFeedDegradationSignalView signal = adapter.updateDegradationSignal(new UpdateHotFeedDegradationSignalRequest(
                false,
                "clear"
        ));

        assertThat(prewarm.loadedCount()).isEqualTo(18);
        assertThat(prewarm.warmedCount()).isEqualTo(18);
        assertThat(signal.degraded()).isFalse();
        ArgumentCaptor<PrewarmHotFeedCacheCommand> prewarmCaptor =
                ArgumentCaptor.forClass(PrewarmHotFeedCacheCommand.class);
        verify(service).prewarm(prewarmCaptor.capture());
        PrewarmHotFeedCacheCommand prewarmCommand = prewarmCaptor.getValue();
        ArgumentCaptor<UpdateHotFeedDegradationSignalCommand> degradationCaptor =
                ArgumentCaptor.forClass(UpdateHotFeedDegradationSignalCommand.class);
        verify(service).updateDegradationSignal(degradationCaptor.capture());
        UpdateHotFeedDegradationSignalCommand degradationCommand = degradationCaptor.getValue();
        assertAll(
                () -> assertEquals("board", prewarmCommand.scope()),
                () -> assertEquals(boardId, prewarmCommand.boardId()),
                () -> assertEquals(20, prewarmCommand.limit()),
                () -> assertEquals("warm board", prewarmCommand.reason()),
                () -> assertEquals(false, degradationCommand.degraded()),
                () -> assertEquals("clear", degradationCommand.reason())
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
