package com.nowcoder.community.user.service;

import com.nowcoder.community.social.application.SocialReadApplicationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import com.nowcoder.community.user.config.UserSocialProfileProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SocialServiceClientTest {

    @Test
    void safeMethodsShouldDegradeAndEmitMetricsWhenDownstreamFails() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        UserSocialProfileProperties properties = new UserSocialProfileProperties();
        properties.setDegradeOnError(true);

        SocialReadApplicationService applicationService = mock(SocialReadApplicationService.class);
        UserSocialProfileService client = new UserSocialProfileService(registry, properties, applicationService);
        RuntimeException downstreamError = new RuntimeException("downstream error");
        when(applicationService.userProfileStats(anyInt(), any())).thenThrow(downstreamError);
        when(applicationService.userLikeCount(anyInt())).thenThrow(downstreamError);
        when(applicationService.followeeCount(anyInt())).thenThrow(downstreamError);
        when(applicationService.followerCount(anyInt())).thenThrow(downstreamError);
        when(applicationService.hasFollowedUser(anyInt(), anyInt())).thenThrow(downstreamError);

        UserSocialProfileService.UserProfileStats stats = client.safeUserProfileStats(1, 2);
        assertThat(stats).isNotNull();
        assertThat(stats.isDegraded()).isTrue();
        assertThat(stats.getLikeCount()).isEqualTo(0);
        assertThat(stats.getFolloweeCount()).isEqualTo(0);
        assertThat(stats.getFollowerCount()).isEqualTo(0);

        assertThat(client.safeUserLikeCount(1)).isEqualTo(0);
        assertThat(client.safeFolloweeCount(1)).isEqualTo(0);
        assertThat(client.safeFollowerCount(1)).isEqualTo(0);
        assertThat(client.safeHasFollowed(1, 2)).isFalse();

        assertThat(registry.find("user_social_profile_requests_total")
                .tags("api", "profileStats", "outcome", "degraded")
                .counter()).isNotNull();
        assertThat(registry.find("user_social_profile_requests_total")
                .tags("api", "profileStats", "outcome", "degraded")
                .counter()
                .count()).isEqualTo(1.0);

        assertThat(registry.find("user_social_profile_requests_total")
                .tags("api", "userLikeCount", "outcome", "degraded")
                .counter()).isNotNull();
        assertThat(registry.find("user_social_profile_requests_total")
                .tags("api", "userLikeCount", "outcome", "degraded")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
