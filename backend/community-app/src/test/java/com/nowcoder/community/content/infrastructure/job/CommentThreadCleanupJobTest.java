package com.nowcoder.community.content.infrastructure.job;

import com.nowcoder.community.content.application.CommentThreadCleanupApplicationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CommentThreadCleanupJobTest {

    @Test
    void cleanupShouldContainTopLevelScanFailureForTheNextScheduledRun() {
        CommentThreadCleanupApplicationService applicationService =
                mock(CommentThreadCleanupApplicationService.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(applicationService).reconcile(25, 4);
        CommentThreadCleanupJob job =
                new CommentThreadCleanupJob(applicationService, true, 25, 4);

        assertThatCode(job::cleanup).doesNotThrowAnyException();

        verify(applicationService).reconcile(25, 4);
    }

    @Test
    void disabledCleanupShouldNotScan() {
        CommentThreadCleanupApplicationService applicationService =
                mock(CommentThreadCleanupApplicationService.class);
        CommentThreadCleanupJob job =
                new CommentThreadCleanupJob(applicationService, false, 25, 4);

        job.cleanup();

        verify(applicationService, never()).reconcile(25, 4);
    }
}
