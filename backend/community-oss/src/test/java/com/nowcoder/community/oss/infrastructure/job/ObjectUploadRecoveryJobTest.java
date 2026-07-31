package com.nowcoder.community.oss.infrastructure.job;

import com.nowcoder.community.oss.application.ObjectUploadRecoveryApplicationService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ObjectUploadRecoveryJobTest {

    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void activeUploadsMustWaitPastTheMaximumPutTimeWhileCancellationsKeepTheirShortRetryCadence() {
        ObjectUploadRecoveryApplicationService applicationService =
                mock(ObjectUploadRecoveryApplicationService.class);
        ObjectUploadRecoveryJob job = new ObjectUploadRecoveryJob(
                applicationService,
                CLOCK,
                true,
                37,
                60,
                300
        );

        job.recover();

        verify(applicationService).recoverStaleUploads(
                NOW.minus(Duration.ofMinutes(35)),
                NOW.minus(Duration.ofMinutes(5)),
                37
        );
    }
}
