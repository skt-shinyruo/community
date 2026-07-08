package com.nowcoder.community.content.infrastructure.job;

import com.nowcoder.community.content.application.HotPathPrewarmApplicationService;
import com.nowcoder.community.content.application.result.HotPathPrewarmResult;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HotPathPrewarmJobTest {

    @Test
    void prewarmShouldSkipWhenDisabled() {
        HotPathPrewarmApplicationService service = mock(HotPathPrewarmApplicationService.class);
        HotPathPrewarmJob job = new HotPathPrewarmJob(service, false);

        job.prewarm();

        verify(service, never()).prewarm();
    }

    @Test
    void prewarmShouldDelegateToApplicationServiceWhenEnabled() {
        HotPathPrewarmApplicationService service = mock(HotPathPrewarmApplicationService.class);
        when(service.prewarm()).thenReturn(new HotPathPrewarmResult(1, 2, 3));
        HotPathPrewarmJob job = new HotPathPrewarmJob(service, true);

        job.prewarm();

        verify(service).prewarm();
    }
}
