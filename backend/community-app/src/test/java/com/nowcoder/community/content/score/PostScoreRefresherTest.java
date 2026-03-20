package com.nowcoder.community.content.score;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.like.LikeQueryService;
import com.nowcoder.community.content.service.PostService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;

class PostScoreRefresherTest {

    @Test
    void refreshSafelyShouldReenqueueOnUnexpectedException() {
        CapturingQueue queue = new CapturingQueue();
        PostService postService = Mockito.mock(PostService.class);
        LikeQueryService likeQueryService = Mockito.mock(LikeQueryService.class);
        PostScoreCommandService scoreCommandService = Mockito.mock(PostScoreCommandService.class);

        PostScoreRefresher refresher = new PostScoreRefresher(
                queue,
                postService,
                likeQueryService,
                scoreCommandService,
                new SimpleMeterRegistry(),
                true,
                1
        );

        Mockito.when(postService.getById(100)).thenThrow(new RuntimeException("db down"));
        refresher.refreshSafely(100);

        assertThat(queue.added).containsExactly(100);
    }

    @Test
    void refreshSafelyShouldDropWhenNotFound() {
        CapturingQueue queue = new CapturingQueue();
        PostService postService = Mockito.mock(PostService.class);
        LikeQueryService likeQueryService = Mockito.mock(LikeQueryService.class);
        PostScoreCommandService scoreCommandService = Mockito.mock(PostScoreCommandService.class);

        PostScoreRefresher refresher = new PostScoreRefresher(
                queue,
                postService,
                likeQueryService,
                scoreCommandService,
                new SimpleMeterRegistry(),
                true,
                1
        );

        Mockito.when(postService.getById(100)).thenThrow(new BusinessException(NOT_FOUND, "post missing"));
        refresher.refreshSafely(100);

        assertThat(queue.added).isEmpty();
    }

    private static class CapturingQueue implements PostScoreQueue {
        private final List<Integer> added = new ArrayList<>();

        @Override
        public void add(int postId) {
            added.add(postId);
        }

        @Override
        public Integer pop() {
            return null;
        }
    }
}
