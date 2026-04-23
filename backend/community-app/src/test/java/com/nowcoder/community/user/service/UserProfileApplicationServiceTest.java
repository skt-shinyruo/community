package com.nowcoder.community.user.service;

import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.api.model.RecentUserCommentView;
import com.nowcoder.community.content.api.query.PostReadQueryApi;
import com.nowcoder.community.growth.api.model.UserLevelSummaryView;
import com.nowcoder.community.growth.api.query.UserLevelQueryApi;
import com.nowcoder.community.user.api.model.UserProfileView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileApplicationServiceTest {

    @Mock
    private UserReadApplicationService userReadApplicationService;

    @Mock
    private UserSocialProfileService userSocialProfileService;

    @Mock
    private PostReadQueryApi postReadQueryApi;

    @Mock
    private UserLevelQueryApi userLevelQueryApi;

    @Test
    void getShouldAssembleRealProfilePageDataAcrossUserOwnedAndForeignApis() {
        UserProfileApplicationService service = new UserProfileApplicationService(
                userReadApplicationService,
                userSocialProfileService,
                postReadQueryApi,
                userLevelQueryApi
        );
        UUID userId = uuid(7);
        UUID viewerId = uuid(42);
        Date createTime = new Date();
        UserSocialProfileService.UserProfileStats stats = new UserSocialProfileService.UserProfileStats();
        stats.setLikeCount(12);
        stats.setFolloweeCount(5);
        stats.setFollowerCount(8);
        stats.setHasFollowed(true);
        stats.setDegraded(false);
        when(userReadApplicationService.getProfile(userId))
                .thenReturn(new UserProfileView(userId, "alice", "h7", 2, 0, createTime, 250, 3, 900L, "ACTIVE"));
        when(userSocialProfileService.userProfileStats(userId, viewerId)).thenReturn(stats);
        when(userLevelQueryApi.evaluateLevel(userId)).thenReturn(new UserLevelSummaryView(4, 13, 30, 7, 14, true));

        UserProfilePageView page = service.get(authentication(viewerId), userId);

        assertThat(page).extracting(
                UserProfilePageView::userId,
                UserProfilePageView::username,
                UserProfilePageView::headerUrl,
                UserProfilePageView::type,
                UserProfilePageView::status,
                UserProfilePageView::createTime,
                UserProfilePageView::score,
                UserProfilePageView::level,
                UserProfilePageView::walletBalance,
                UserProfilePageView::walletStatus,
                UserProfilePageView::userLevelEnabled,
                UserProfilePageView::userLevel,
                UserProfilePageView::signInDaysInWindow,
                UserProfilePageView::likeCount,
                UserProfilePageView::followeeCount,
                UserProfilePageView::followerCount,
                UserProfilePageView::hasFollowed,
                UserProfilePageView::socialDegraded
        ).containsExactly(
                userId, "alice", "h7", 2, 0, createTime, 250, 3, 900L, "ACTIVE",
                true, 4, 13, 12L, 5L, 8L, true, false
        );

        verify(userReadApplicationService).getProfile(userId);
        verify(userSocialProfileService).userProfileStats(userId, viewerId);
        verify(userLevelQueryApi).evaluateLevel(userId);
    }

    @Test
    void getShouldUseAnonymousViewerStatsWithoutSocialPlaceholderFallback() {
        UserProfileApplicationService service = new UserProfileApplicationService(
                userReadApplicationService,
                userSocialProfileService,
                postReadQueryApi,
                userLevelQueryApi
        );
        UUID userId = uuid(7);
        Date createTime = new Date();
        UserSocialProfileService.UserProfileStats stats = new UserSocialProfileService.UserProfileStats();
        stats.setLikeCount(2);
        stats.setFolloweeCount(3);
        stats.setFollowerCount(4);
        stats.setHasFollowed(false);
        stats.setDegraded(false);
        when(userReadApplicationService.getProfile(userId))
                .thenReturn(new UserProfileView(userId, "alice", "h7", 2, 0, createTime, 250, 3, 900L, "ACTIVE"));
        when(userSocialProfileService.userProfileStats(userId, null)).thenReturn(stats);
        when(userLevelQueryApi.evaluateLevel(userId)).thenReturn(new UserLevelSummaryView(4, 13, 30, 7, 14, true));

        UserProfilePageView page = service.get(null, userId);

        assertThat(page.likeCount()).isEqualTo(2L);
        assertThat(page.followeeCount()).isEqualTo(3L);
        assertThat(page.followerCount()).isEqualTo(4L);
        assertThat(page.hasFollowed()).isFalse();
        assertThat(page.socialDegraded()).isFalse();
        verify(userSocialProfileService).userProfileStats(userId, null);
    }

    @Test
    void getShouldUseSelfViewerStatsWhenViewerMatchesTargetUser() {
        UserProfileApplicationService service = new UserProfileApplicationService(
                userReadApplicationService,
                userSocialProfileService,
                postReadQueryApi,
                userLevelQueryApi
        );
        UUID userId = uuid(7);
        Date createTime = new Date();
        UserSocialProfileService.UserProfileStats stats = new UserSocialProfileService.UserProfileStats();
        stats.setLikeCount(7);
        stats.setFolloweeCount(8);
        stats.setFollowerCount(9);
        stats.setHasFollowed(false);
        stats.setDegraded(false);
        when(userReadApplicationService.getProfile(userId))
                .thenReturn(new UserProfileView(userId, "alice", "h7", 2, 0, createTime, 250, 3, 900L, "ACTIVE"));
        when(userSocialProfileService.userProfileStats(userId, userId)).thenReturn(stats);
        when(userLevelQueryApi.evaluateLevel(userId)).thenReturn(new UserLevelSummaryView(4, 13, 30, 7, 14, true));

        UserProfilePageView page = service.get(authentication(userId), userId);

        assertThat(page.likeCount()).isEqualTo(7L);
        assertThat(page.followeeCount()).isEqualTo(8L);
        assertThat(page.followerCount()).isEqualTo(9L);
        assertThat(page.hasFollowed()).isFalse();
        assertThat(page.socialDegraded()).isFalse();
        verify(userSocialProfileService).userProfileStats(userId, userId);
    }

    @Test
    void getShouldHideLevelFieldsWhenLevelProjectionIsDisabled() {
        UserProfileApplicationService service = new UserProfileApplicationService(
                userReadApplicationService,
                userSocialProfileService,
                postReadQueryApi,
                userLevelQueryApi
        );
        UUID userId = uuid(7);
        UUID viewerId = uuid(42);
        Date createTime = new Date();
        UserSocialProfileService.UserProfileStats stats = new UserSocialProfileService.UserProfileStats();
        stats.setHasFollowed(false);
        when(userReadApplicationService.getProfile(userId))
                .thenReturn(new UserProfileView(userId, "alice", "h7", 2, 0, createTime, 250, 3, 900L, "ACTIVE"));
        when(userSocialProfileService.userProfileStats(userId, viewerId)).thenReturn(stats);
        when(userLevelQueryApi.evaluateLevel(userId)).thenReturn(new UserLevelSummaryView(1, 0, 100, 12, 88, false));

        UserProfilePageView page = service.get(authentication(viewerId), userId);

        assertThat(page.userLevelEnabled()).isFalse();
        assertThat(page.userLevel()).isNull();
        assertThat(page.signInDaysInWindow()).isNull();
    }

    @Test
    void getShouldHideLevelFieldsWhenLevelProjectionIsNull() {
        UserProfileApplicationService service = new UserProfileApplicationService(
                userReadApplicationService,
                userSocialProfileService,
                postReadQueryApi,
                userLevelQueryApi
        );
        UUID userId = uuid(7);
        UUID viewerId = uuid(42);
        Date createTime = new Date();
        UserSocialProfileService.UserProfileStats stats = new UserSocialProfileService.UserProfileStats();
        stats.setHasFollowed(false);
        when(userReadApplicationService.getProfile(userId))
                .thenReturn(new UserProfileView(userId, "alice", "h7", 2, 0, createTime, 250, 3, 900L, "ACTIVE"));
        when(userSocialProfileService.userProfileStats(userId, viewerId)).thenReturn(stats);
        when(userLevelQueryApi.evaluateLevel(userId)).thenReturn(null);

        UserProfilePageView page = service.get(authentication(viewerId), userId);

        assertThat(page.userLevelEnabled()).isFalse();
        assertThat(page.userLevel()).isNull();
        assertThat(page.signInDaysInWindow()).isNull();
    }

    @Test
    void listRecentPostsShouldRequireUserExistenceWithoutLoadingWalletProfileAndMapNestedPostFields() {
        UserProfileApplicationService service = new UserProfileApplicationService(
                userReadApplicationService,
                userSocialProfileService,
                postReadQueryApi,
                userLevelQueryApi
        );
        UUID userId = uuid(7);
        UUID postId = uuid(11);
        UUID categoryId = uuid(3);
        UUID lastReplyUserId = uuid(8);
        Date createTime = new Date();
        Date lastReplyTime = new Date(createTime.getTime() + 1_000L);
        Date lastActivityTime = new Date(createTime.getTime() + 2_000L);
        when(postReadQueryApi.listPostsByUser(userId, 1, 5))
                .thenReturn(List.of(new PostSummaryView(
                        postId,
                        userId,
                        "first post",
                        1,
                        0,
                        createTime,
                        4,
                        9.5,
                        categoryId,
                        List.of("java", "spring"),
                        lastReplyUserId,
                        lastReplyTime,
                        lastActivityTime,
                        "latest reply"
                )));

        List<UserProfilePageView.RecentPostSummaryView> items = service.listRecentPosts(userId, 1, 5);

        assertThat(items).containsExactly(new UserProfilePageView.RecentPostSummaryView(
                postId,
                userId,
                "first post",
                1,
                0,
                createTime,
                4,
                9.5,
                categoryId,
                List.of("java", "spring"),
                lastReplyUserId,
                lastReplyTime,
                lastActivityTime,
                "latest reply"
        ));
        verify(userReadApplicationService).requireExistingUser(userId);
        verify(userReadApplicationService, never()).getProfile(userId);
        verify(postReadQueryApi).listPostsByUser(userId, 1, 5);
    }

    @Test
    void listRecentCommentsShouldRequireUserExistenceWithoutLoadingWalletProfileAndMapNestedCommentFields() {
        UserProfileApplicationService service = new UserProfileApplicationService(
                userReadApplicationService,
                userSocialProfileService,
                postReadQueryApi,
                userLevelQueryApi
        );
        UUID userId = uuid(7);
        UUID commentId = uuid(21);
        UUID entityId = uuid(101);
        UUID targetId = uuid(301);
        UUID postId = uuid(201);
        Date createTime = new Date();
        when(postReadQueryApi.listRecentCommentsByUser(userId, 2, 10))
                .thenReturn(List.of(new RecentUserCommentView(
                        commentId,
                        userId,
                        1,
                        entityId,
                        targetId,
                        postId,
                        "post title",
                        "reply body",
                        createTime
                )));

        List<UserProfilePageView.RecentCommentItemView> items = service.listRecentComments(userId, 2, 10);

        assertThat(items).containsExactly(new UserProfilePageView.RecentCommentItemView(
                commentId,
                userId,
                1,
                entityId,
                targetId,
                postId,
                "post title",
                "reply body",
                createTime
        ));
        verify(userReadApplicationService).requireExistingUser(userId);
        verify(userReadApplicationService, never()).getProfile(userId);
        verify(postReadQueryApi).listRecentCommentsByUser(userId, 2, 10);
    }

    private Authentication authentication(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return new TestingAuthenticationToken(jwt, null);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
