package com.nowcoder.community.search.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReindexJobServiceTest {

    @Test
    void tryStartShouldAcquireFirstJob() {
        ReindexJobService service = new ReindexJobService();

        ReindexJobService.ReindexJob job = service.tryStart();

        assertNotNull(job);
        assertTrue(job.acquired());
        assertNotNull(job.jobId());
    }

    @Test
    void tryStartWhenAlreadyRunningShouldReturnExistingJobId() {
        ReindexJobService service = new ReindexJobService();
        ReindexJobService.ReindexJob running = service.tryStart();

        ReindexJobService.ReindexJob contender = service.tryStart();

        assertFalse(contender.acquired());
        assertTrue(running.jobId().equals(contender.jobId()));
    }

    @Test
    void finishShouldReleaseJobForNextRun() {
        ReindexJobService service = new ReindexJobService();
        ReindexJobService.ReindexJob first = service.tryStart();

        service.finish(first.jobId());
        ReindexJobService.ReindexJob second = service.tryStart();

        assertTrue(second.acquired());
        assertNotEquals(first.jobId(), second.jobId());
    }

    @Test
    void startRenewalShouldBeNoopHandleInSingleProcessMode() throws Exception {
        ReindexJobService service = new ReindexJobService();
        ReindexJobService.ReindexJob job = service.tryStart();

        try (ReindexJobService.RenewalHandle ignored = service.startRenewal(job.jobId())) {
            assertNotNull(ignored);
        }
    }
}
