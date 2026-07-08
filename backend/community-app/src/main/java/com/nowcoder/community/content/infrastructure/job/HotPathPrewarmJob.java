package com.nowcoder.community.content.infrastructure.job;

import com.nowcoder.community.common.trace.TraceJobRunner;
import com.nowcoder.community.content.application.HotPathPrewarmApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HotPathPrewarmJob {

    private static final Logger log = LoggerFactory.getLogger(HotPathPrewarmJob.class);
    private static final String JOB_NAME = "content-hot-path-prewarm";

    private final HotPathPrewarmApplicationService hotPathPrewarmApplicationService;
    private final boolean enabled;

    public HotPathPrewarmJob(
            HotPathPrewarmApplicationService hotPathPrewarmApplicationService,
            @Value("${content.hot-path.prewarm.enabled:true}") boolean enabled
    ) {
        this.hotPathPrewarmApplicationService = hotPathPrewarmApplicationService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${content.hot-path.prewarm.delay-ms:60000}")
    public void prewarm() {
        TraceJobRunner.run(JOB_NAME, () -> {
            if (!enabled) {
                return;
            }
            try {
                hotPathPrewarmApplicationService.prewarm();
            } catch (RuntimeException e) {
                log.warn("[content-hot-path] prewarm failed: {}", e.toString());
            }
        });
    }
}
