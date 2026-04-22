package com.nowcoder.community.user.app.query;

import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.api.model.RecentUserCommentView;
import com.nowcoder.community.content.api.query.PostReadQueryApi;
import com.nowcoder.community.growth.api.model.UserLevelSummaryView;
import com.nowcoder.community.growth.api.query.UserLevelQueryApi;
import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import com.nowcoder.community.user.service.UserSocialProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GetUserProfilePageQueryTest {

    @Test
    void getShouldProjectProfileWithDegradedSocialStateWhileMigrationIsInProgress() {
        UserProfileQueryApi userProfileQueryApi = mock(UserProfileQueryApi.class);
        UserSocialProfileService userSocialProfileService = mock(UserSocialProfileService.class);
        PostReadQueryApi postReadQueryApi = mock(PostReadQueryApi.class);
        UserLevelQueryApi userLevelQueryApi = mock(UserLevelQueryApi.class);
        GetUserProfilePageQuery query = new GetUserProfilePageQuery(
                userProfileQueryApi,
                userSocialProfileService,
                postReadQueryApi,
                userLevelQueryApi
        );
        UUID userId = uuid(7);
        Date createTime = new Date();
        when(userProfileQueryApi.getProfile(userId))
                .thenReturn(new UserProfileView(userId, "alice", "h7", 2, 0, createTime, 250, 3, 900L, "ACTIVE"));

        UserProfilePageView page = query.get(null, userId);

        assertThat(page.userId()).isEqualTo(userId);
        assertThat(page.username()).isEqualTo("alice");
        assertThat(page.headerUrl()).isEqualTo("h7");
        assertThat(page.type()).isEqualTo(2);
        assertThat(page.status()).isEqualTo(0);
        assertThat(page.createTime()).isEqualTo(createTime);
        assertThat(page.score()).isEqualTo(250);
        assertThat(page.level()).isEqualTo(3);
        assertThat(page.walletBalance()).isEqualTo(900L);
        assertThat(page.walletStatus()).isEqualTo("ACTIVE");
        assertThat(page.userLevelEnabled()).isFalse();
        assertThat(page.userLevel()).isNull();
        assertThat(page.signInDaysInWindow()).isNull();
        assertThat(page.likeCount()).isZero();
        assertThat(page.followeeCount()).isZero();
        assertThat(page.followerCount()).isZero();
        assertThat(page.hasFollowed()).isFalse();
        assertThat(page.socialDegraded()).isTrue();
        verify(userProfileQueryApi).getProfile(userId);
        verifyNoInteractions(userSocialProfileService, postReadQueryApi, userLevelQueryApi);
    }

    @Test
    void getShouldParseUuidViewerWithoutChangingDegradedSocialProjection() {
        UserProfileQueryApi userProfileQueryApi = mock(UserProfileQueryApi.class);
        UserSocialProfileService userSocialProfileService = mock(UserSocialProfileService.class);
        PostReadQueryApi postReadQueryApi = mock(PostReadQueryApi.class);
        UserLevelQueryApi userLevelQueryApi = mock(UserLevelQueryApi.class);
        GetUserProfilePageQuery query = new GetUserProfilePageQuery(
                userProfileQueryApi,
                userSocialProfileService,
                postReadQueryApi,
                userLevelQueryApi
        );
        UUID userId = uuid(7);
        UUID viewerId = uuid(42);
        Date createTime = new Date();
        when(userProfileQueryApi.getProfile(userId))
                .thenReturn(new UserProfileView(userId, "alice", "h7", 2, 0, createTime, 250, 3, 900L, "ACTIVE"));

        UserProfilePageView page = query.get(authentication(viewerId), userId);

        assertThat(page.userId()).isEqualTo(userId);
        assertThat(page.hasFollowed()).isFalse();
        assertThat(page.socialDegraded()).isTrue();
        verify(userProfileQueryApi).getProfile(userId);
        verifyNoInteractions(userSocialProfileService, postReadQueryApi, userLevelQueryApi);
    }

    @Test
    void listRecentPostsShouldReturnEmptyWhileProjectionIsTemporarilyDisabled() {
        UserProfileQueryApi userProfileQueryApi = mock(UserProfileQueryApi.class);
        UserSocialProfileService userSocialProfileService = mock(UserSocialProfileService.class);
        PostReadQueryApi postReadQueryApi = mock(PostReadQueryApi.class);
        UserLevelQueryApi userLevelQueryApi = mock(UserLevelQueryApi.class);
        GetUserProfilePageQuery query = new GetUserProfilePageQuery(
                userProfileQueryApi,
                userSocialProfileService,
                postReadQueryApi,
                userLevelQueryApi
        );
        UUID userId = uuid(7);
        Date createTime = new Date();
        when(userProfileQueryApi.getProfile(userId))
                .thenReturn(new UserProfileView(userId, "alice", "h7", 2, 0, createTime, 250, 3, 900L, "ACTIVE"));

        var items = query.listRecentPosts(userId, 1, 5);

        assertThat(items).isEmpty();
        verify(userProfileQueryApi).getProfile(userId);
        verifyNoInteractions(postReadQueryApi, userSocialProfileService, userLevelQueryApi);
    }

    @Test
    void listRecentCommentsShouldReturnEmptyWhileProjectionIsTemporarilyDisabled() {
        UserProfileQueryApi userProfileQueryApi = mock(UserProfileQueryApi.class);
        UserSocialProfileService userSocialProfileService = mock(UserSocialProfileService.class);
        PostReadQueryApi postReadQueryApi = mock(PostReadQueryApi.class);
        UserLevelQueryApi userLevelQueryApi = mock(UserLevelQueryApi.class);
        GetUserProfilePageQuery query = new GetUserProfilePageQuery(
                userProfileQueryApi,
                userSocialProfileService,
                postReadQueryApi,
                userLevelQueryApi
        );
        UUID userId = uuid(7);
        Date createTime = new Date();
        when(userProfileQueryApi.getProfile(userId))
                .thenReturn(new UserProfileView(userId, "alice", "h7", 2, 0, createTime, 250, 3, 900L, "ACTIVE"));

        var items = query.listRecentComments(userId, 2, 10);

        assertThat(items).isEmpty();
        verify(userProfileQueryApi).getProfile(userId);
        verifyNoInteractions(postReadQueryApi, userSocialProfileService, userLevelQueryApi);
    }

    private Authentication authentication(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        return authentication;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
