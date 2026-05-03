package com.nowcoder.community.content.infrastructure.job;

import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceId;
import com.nowcoder.community.content.application.PostScoreRefreshApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PostScoreRefresherTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void refreshBatchShouldDelegateToApplicationServiceWhenEnabled() {
        PostScoreRefreshApplicationService refreshApplicationService = mock(PostScoreRefreshApplicationService.class);
        PostScoreRefresher refresher = new PostScoreRefresher(refreshApplicationService, true, 3);

        refresher.refreshBatch();

        verify(refreshApplicationService).refreshBatch(3);
    }

    @Test
    void refreshBatchShouldDoNothingWhenDisabled() {
        PostScoreRefreshApplicationService refreshApplicationService = mock(PostScoreRefreshApplicationService.class);
        PostScoreRefresher refresher = new PostScoreRefresher(refreshApplicationService, false, 3);

        refresher.refreshBatch();

        verifyNoInteractions(refreshApplicationService);
    }

    @Test
    void refreshBatchShouldRunApplicationServiceWithJobTraceAndRestoreAfterwards() {
        PostScoreRefreshApplicationService refreshApplicationService = mock(PostScoreRefreshApplicationService.class);
        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(invocation -> {
            seen.set(TraceId.get());
            return null;
        }).when(refreshApplicationService).refreshBatch(3);
        PostScoreRefresher refresher = new PostScoreRefresher(refreshApplicationService, true, 3);

        refresher.refreshBatch();

        assertThat(seen.get()).matches("[0-9a-f]{32}");
        assertThat(TraceId.get()).isNull();
    }
}
