package com.nowcoder.community.social.service;

import com.nowcoder.community.common.event.payload.FollowPayload;
import com.nowcoder.community.social.event.InMemorySocialEventPublisher;
import com.nowcoder.community.social.follow.FollowService;
import com.nowcoder.community.social.follow.InMemoryFollowRepository;
import com.nowcoder.community.social.follow.dto.FollowRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FollowServiceTest {

    @Test
    void followShouldBeIdempotentAndPublishOnce() {
        InMemoryFollowRepository repo = new InMemoryFollowRepository();
        InMemorySocialEventPublisher publisher = new InMemorySocialEventPublisher();
        FollowService service = new FollowService(repo, publisher);

        FollowRequest req = new FollowRequest();
        req.setEntityType(3);
        req.setEntityId(2);
        req.setEntityUserId(2);

        service.follow(1, req);
        assertThat(service.hasFollowed(1, 3, 2)).isTrue();
        assertThat(service.followeeCount(1, 3)).isEqualTo(1);
        assertThat(service.followerCount(3, 2)).isEqualTo(1);
        assertThat(publisher.snapshot()).hasSize(1);
        assertThat(publisher.snapshot().get(0)).isInstanceOf(FollowPayload.class);

        service.follow(1, req);
        assertThat(service.hasFollowed(1, 3, 2)).isTrue();
        assertThat(service.followeeCount(1, 3)).isEqualTo(1);
        assertThat(service.followerCount(3, 2)).isEqualTo(1);
        assertThat(publisher.snapshot()).hasSize(1);

        service.unfollow(1, 3, 2);
        assertThat(service.hasFollowed(1, 3, 2)).isFalse();
        assertThat(service.followeeCount(1, 3)).isEqualTo(0);
        assertThat(service.followerCount(3, 2)).isEqualTo(0);
        assertThat(publisher.snapshot()).hasSize(1);
    }
}
