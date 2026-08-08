package com.nowcoder.community.content.infrastructure.job;

import com.nowcoder.community.content.application.PostCounterApplicationService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PostCounterSnapshotFlushJobTest {

    @Test
    void flushShouldReconcileBookmarkCountersBeforeNormalSnapshotFlush() {
        PostCounterApplicationService service = mock(PostCounterApplicationService.class);
        PostCounterSnapshotFlushJob job = new PostCounterSnapshotFlushJob(service, true, 25);

        job.flush();

        var ordered = inOrder(service);
        ordered.verify(service).reconcileBookmarkCounters(25);
        ordered.verify(service).flushSnapshots(25);
    }

    @Test
    void flushShouldContinueNormalSnapshotFlushWhenReconciliationFails() {
        PostCounterApplicationService service = mock(PostCounterApplicationService.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("marker store unavailable"))
                .when(service).reconcileBookmarkCounters(25);
        PostCounterSnapshotFlushJob job = new PostCounterSnapshotFlushJob(service, true, 25);

        job.flush();

        verify(service).flushSnapshots(25);
    }
}
