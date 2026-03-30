package com.nowcoder.community.user.service;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.social.api.query.SocialFollowQueryApi;
import com.nowcoder.community.social.api.query.SocialLikeQueryApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserSocialProfileServiceTest {

    @Test
    void userProfileStatsShouldReturnAggregatedCounts() {
        SocialLikeQueryApi likeQueryApi = mock(SocialLikeQueryApi.class);
        SocialFollowQueryApi followQueryApi = mock(SocialFollowQueryApi.class);
        UserSocialProfileService service = new UserSocialProfileService(likeQueryApi, followQueryApi);

        when(likeQueryApi.userLikeCount(1)).thenReturn(5L);
        when(followQueryApi.followeeCount(1, EntityTypes.USER)).thenReturn(2L);
        when(followQueryApi.followerCount(EntityTypes.USER, 1)).thenReturn(3L);
        when(followQueryApi.hasFollowed(2, EntityTypes.USER, 1)).thenReturn(true);

        UserSocialProfileService.UserProfileStats stats = service.userProfileStats(1, 2);

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
        when(likeQueryApi.userLikeCount(anyInt())).thenThrow(error);

        assertThatThrownBy(() -> service.userProfileStats(1, 2))
                .isSameAs(error);
    }
}
