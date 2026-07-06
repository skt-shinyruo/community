package com.nowcoder.community.content.infrastructure.job;

import com.nowcoder.community.common.trace.TraceJobRunner;
import com.nowcoder.community.content.application.PostCounterApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PostCounterSnapshotFlushJob {

    private static final Logger log = LoggerFactory.getLogger(PostCounterSnapshotFlushJob.class);
    private static final String JOB_NAME = "post-counter-snapshot-flush";

    private final PostCounterApplicationService postCounterApplicationService;
    private final boolean enabled;
    private final int batchSize;

    public PostCounterSnapshotFlushJob(
            PostCounterApplicationService postCounterApplicationService,
            @Value("${content.counter.flush.enabled:true}") boolean enabled,
            @Value("${content.counter.flush.batch-size:200}") int batchSize
    ) {
        this.postCounterApplicationService = postCounterApplicationService;
        this.enabled = enabled;
        this.batchSize = Math.max(1, Math.min(2000, batchSize));
    }

    @Scheduled(fixedDelayString = "${content.counter.flush.delay-ms:30000}")
    public void flush() {
        TraceJobRunner.run(JOB_NAME, () -> {
            if (!enabled) {
                return;
            }
            try {
                postCounterApplicationService.flushSnapshots(batchSize);
            } catch (RuntimeException e) {
                log.warn("[content-counter] snapshot flush failed: {}", e.toString());
            }
        });
    }
}
