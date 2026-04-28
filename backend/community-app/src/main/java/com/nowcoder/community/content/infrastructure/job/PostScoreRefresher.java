package com.nowcoder.community.content.infrastructure.job;

import com.nowcoder.community.content.application.PostScoreRefreshApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PostScoreRefresher {

    private final PostScoreRefreshApplicationService refreshApplicationService;
    private final boolean enabled;
    private final int batchSize;

    public PostScoreRefresher(
            PostScoreRefreshApplicationService refreshApplicationService,
            @Value("${content.score.refresh.enabled:true}") boolean enabled,
            @Value("${content.score.refresh.batch-size:200}") int batchSize
    ) {
        this.refreshApplicationService = refreshApplicationService;
        this.enabled = enabled;
        this.batchSize = Math.max(1, Math.min(2000, batchSize));
    }

    @Scheduled(fixedDelayString = "${content.score.refresh.delay-ms:30000}")
    public void refreshBatch() {
        if (!enabled) {
            return;
        }
        refreshApplicationService.refreshBatch(batchSize);
    }
}
