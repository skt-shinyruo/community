package com.nowcoder.community.social.application;

import com.nowcoder.community.contracts.domain.EntityTypes;
import com.nowcoder.community.social.application.dto.SocialUserProfileStats;
import com.nowcoder.community.social.follow.FollowService;
import com.nowcoder.community.social.like.LikeService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialReadApplicationServiceTest {

    @Test
    void entityLikeCountShouldReturnZeroForInvalidArguments() {
        LikeService likeService = mock(LikeService.class);
        FollowService followService = mock(FollowService.class);
        SocialReadApplicationService service = new SocialReadApplicationService(likeService, followService);

        assertThat(service.entityLikeCount(0, 10)).isZero();
        assertThat(service.entityLikeCount(EntityTypes.POST, 0)).isZero();
    }

    @Test
    void userProfileStatsShouldComposeLikeAndFollowData() {
        LikeService likeService = mock(LikeService.class);
        FollowService followService = mock(FollowService.class);
        SocialReadApplicationService service = new SocialReadApplicationService(likeService, followService);

        when(likeService.userLikeCount(7)).thenReturn(12L);
        when(followService.followeeCount(7, EntityTypes.USER)).thenReturn(3L);
        when(followService.followerCount(EntityTypes.USER, 7)).thenReturn(4L);
        when(followService.hasFollowed(8, EntityTypes.USER, 7)).thenReturn(true);

        SocialUserProfileStats stats = service.userProfileStats(7, 8);

        assertThat(stats.getLikeCount()).isEqualTo(12L);
        assertThat(stats.getFolloweeCount()).isEqualTo(3L);
        assertThat(stats.getFollowerCount()).isEqualTo(4L);
        assertThat(stats.isHasFollowed()).isTrue();
        verify(likeService).userLikeCount(7);
        verify(followService).followeeCount(7, EntityTypes.USER);
        verify(followService).followerCount(EntityTypes.USER, 7);
        verify(followService).hasFollowed(8, EntityTypes.USER, 7);
    }
}
