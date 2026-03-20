package com.nowcoder.community.user.service;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.social.follow.FollowService;
import com.nowcoder.community.social.like.LikeService;
import com.nowcoder.community.user.config.UserSocialProfileProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserSocialProfileServiceTest {

    @Test
    void safeMethodsShouldDegradeAndEmitMetricsWhenDownstreamFails() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        UserSocialProfileProperties properties = new UserSocialProfileProperties();
        properties.setDegradeOnError(true);

        LikeService likeService = mock(LikeService.class);
        FollowService followService = mock(FollowService.class);
        UserSocialProfileService service = new UserSocialProfileService(registry, properties, likeService, followService);
        RuntimeException downstreamError = new RuntimeException("downstream error");
        when(likeService.userLikeCount(anyInt())).thenThrow(downstreamError);
        when(followService.followeeCount(anyInt(), anyInt())).thenThrow(downstreamError);
        when(followService.followerCount(anyInt(), anyInt())).thenThrow(downstreamError);
        when(followService.hasFollowed(anyInt(), anyInt(), anyInt())).thenThrow(downstreamError);

        UserSocialProfileService.UserProfileStats stats = service.safeUserProfileStats(1, 2);
        assertThat(stats).isNotNull();
        assertThat(stats.isDegraded()).isTrue();
        assertThat(stats.getLikeCount()).isEqualTo(0);
        assertThat(stats.getFolloweeCount()).isEqualTo(0);
        assertThat(stats.getFollowerCount()).isEqualTo(0);

        assertThat(service.safeUserLikeCount(1)).isEqualTo(0);
        assertThat(service.safeFolloweeCount(1)).isEqualTo(0);
        assertThat(service.safeFollowerCount(1)).isEqualTo(0);
        assertThat(service.safeHasFollowed(1, 2)).isFalse();

        assertThat(registry.find("internal_call_requests_total")
                .tags("module", "social", "api", "profileStats", "outcome", "degraded")
                .counter()).isNotNull();
        assertThat(registry.find("internal_call_requests_total")
                .tags("module", "social", "api", "profileStats", "outcome", "degraded")
                .counter()
                .count()).isEqualTo(1.0);

        assertThat(registry.find("internal_call_requests_total")
                .tags("module", "social", "api", "userLikeCount", "outcome", "degraded")
                .counter()).isNotNull();
        assertThat(registry.find("internal_call_requests_total")
                .tags("module", "social", "api", "userLikeCount", "outcome", "degraded")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
