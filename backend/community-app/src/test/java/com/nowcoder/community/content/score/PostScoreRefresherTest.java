package com.nowcoder.community.content.score;

import com.nowcoder.community.content.service.PostScoreRefreshApplicationService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PostScoreRefresherTest {

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
}
