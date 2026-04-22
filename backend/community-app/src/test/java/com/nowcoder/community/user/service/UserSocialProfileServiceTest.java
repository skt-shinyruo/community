package com.nowcoder.community.user.service;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.social.api.query.SocialFollowQueryApi;
import com.nowcoder.community.social.api.query.SocialLikeQueryApi;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserSocialProfileServiceTest {

    @Test
    void userProfileStatsShouldReturnAggregatedCounts() {
        SocialLikeQueryApi likeQueryApi = mock(SocialLikeQueryApi.class);
        SocialFollowQueryApi followQueryApi = mock(SocialFollowQueryApi.class);
        UserSocialProfileService service = new UserSocialProfileService(likeQueryApi, followQueryApi);
        UUID userId = uuid(1);
        UUID viewerId = uuid(2);

        when(likeQueryApi.userLikeCount(userId)).thenReturn(5L);
        when(followQueryApi.followeeCount(userId, EntityTypes.USER)).thenReturn(2L);
        when(followQueryApi.followerCount(EntityTypes.USER, userId)).thenReturn(3L);
        when(followQueryApi.hasFollowed(viewerId, EntityTypes.USER, userId)).thenReturn(true);

        UserSocialProfileService.UserProfileStats stats = service.userProfileStats(userId, viewerId);

        assertThat(stats.getLikeCount()).isEqualTo(5L);
        assertThat(stats.getFolloweeCount()).isEqualTo(2L);
        assertThat(stats.getFollowerCount()).isEqualTo(3L);
        assertThat(stats.isHasFollowed()).isTrue();
        assertThat(stats.isDegraded()).isFalse();
    }

    @Test
    void userProfileStatsShouldPropagateUnexpectedErrors() {
        SocialLikeQueryApi likeQueryApi = mock(SocialLikeQueryApi.class);
        SocialFollowQueryApi followQueryApi = mock(SocialFollowQueryApi.class);
        UserSocialProfileService service = new UserSocialProfileService(likeQueryApi, followQueryApi);

        RuntimeException error = new RuntimeException("boom");
        when(likeQueryApi.userLikeCount(any(UUID.class))).thenThrow(error);

        assertThatThrownBy(() -> service.userProfileStats(uuid(1), uuid(2)))
                .isSameAs(error);
    }
}
