package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.content.domain.model.DiscussPost;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class PostHotnessDomainServiceTest {

    @SuppressWarnings("deprecation")
    @Test
    void eventArrivalSignalShouldNotChangeScoreComputedFromCurrentFacts() {
        DiscussPost post = new DiscussPost();
        post.setStatus(DiscussPost.STATUS_WONDERFUL);
        post.setCommentCount(6);
        post.setCreateTime(Date.from(Instant.parse("2026-08-01T00:00:00Z")));
        PostHotnessDomainService service = new PostHotnessDomainService();

        double createdEventScore = service.recomputeScore(post, 9L, 1.0);
        double removedEventScore = service.recomputeScore(post, 9L, -1.0);

        assertThat(createdEventScore)
                .isEqualTo(removedEventScore)
                .isEqualTo(service.recomputeScore(post, 9L));
    }
}
