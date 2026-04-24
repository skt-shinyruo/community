package com.nowcoder.community.content.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.like.LikeQueryService;
import com.nowcoder.community.content.score.PostScoreQueue;
import com.nowcoder.community.content.score.PostScoreUpdateService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class PostScoreRefreshApplicationServiceTest {

    @Test
    void refreshBatchShouldReenqueueOnUnexpectedException() {
        CapturingQueue queue = new CapturingQueue();
        PostService postService = Mockito.mock(PostService.class);
        LikeQueryService likeQueryService = Mockito.mock(LikeQueryService.class);
        PostScoreUpdateService scoreUpdateService = Mockito.mock(PostScoreUpdateService.class);
        UUID postId = uuid(100);
        queue.toPop.add(postId);

        PostScoreRefreshApplicationService refreshApplicationService = new PostScoreRefreshApplicationService(
                queue,
                postService,
                likeQueryService,
                scoreUpdateService,
                new SimpleMeterRegistry()
        );

        Mockito.when(postService.getById(postId)).thenThrow(new RuntimeException("db down"));
        refreshApplicationService.refreshBatch(1);

        assertThat(queue.reenqueued).containsExactly(postId);
    }

    @Test
    void refreshBatchShouldDropWhenNotFound() {
        CapturingQueue queue = new CapturingQueue();
        PostService postService = Mockito.mock(PostService.class);
        LikeQueryService likeQueryService = Mockito.mock(LikeQueryService.class);
        PostScoreUpdateService scoreUpdateService = Mockito.mock(PostScoreUpdateService.class);
        UUID postId = uuid(100);
        queue.toPop.add(postId);

        PostScoreRefreshApplicationService refreshApplicationService = new PostScoreRefreshApplicationService(
                queue,
                postService,
                likeQueryService,
                scoreUpdateService,
                new SimpleMeterRegistry()
        );

        Mockito.when(postService.getById(postId)).thenThrow(new BusinessException(NOT_FOUND, "post missing"));
        refreshApplicationService.refreshBatch(1);

        assertThat(queue.reenqueued).isEmpty();
    }

    private static class CapturingQueue implements PostScoreQueue {
        private final List<UUID> toPop = new ArrayList<>();
        private final List<UUID> reenqueued = new ArrayList<>();

        @Override
        public void add(UUID postId) {
        }

        @Override
        public UUID pop() {
            if (toPop.isEmpty()) {
                return null;
            }
            return toPop.remove(0);
        }

        @Override
        public void reenqueue(UUID postId) {
            reenqueued.add(postId);
        }
    }
}
