package com.nowcoder.community.search.service;

import com.nowcoder.community.infra.scheduler.SingleFlightTaskGuard;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReindexJobServiceTest {

    @Test
    void tryStartShouldReturnAcquiredJobWhenDistributedLockSucceeds() {
        SingleFlightTaskGuard guard = mock(SingleFlightTaskGuard.class);
        SingleFlightTaskGuard.Lock lock = new SingleFlightTaskGuard.Lock("sf:task:search:reindex", "token-1");
        when(guard.tryAcquire("search:reindex", Duration.ofMinutes(30))).thenReturn(lock);

        ReindexJobService service = new ReindexJobService(guard, Duration.ofMinutes(30));
        ReindexJobService.ReindexJob job = service.tryStart();

        assertThat(job.acquired()).isTrue();
        assertThat(job.jobId()).isNotBlank();
        assertThat(job.lock()).isEqualTo(lock);
    }

    @Test
    void finishShouldReleaseDistributedLockFromJobHandle() {
        SingleFlightTaskGuard guard = mock(SingleFlightTaskGuard.class);
        SingleFlightTaskGuard.Lock lock = new SingleFlightTaskGuard.Lock("sf:task:search:reindex", "token-1");
        when(guard.tryAcquire("search:reindex", Duration.ofMinutes(30))).thenReturn(lock);

        ReindexJobService service = new ReindexJobService(guard, Duration.ofMinutes(30));
        ReindexJobService.ReindexJob job = service.tryStart();

        service.finish(job);

        verify(guard).release(lock);
    }
}
