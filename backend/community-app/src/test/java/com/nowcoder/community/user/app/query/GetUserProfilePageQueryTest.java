package com.nowcoder.community.user.app.query;

import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.api.model.RecentUserCommentView;
import com.nowcoder.community.content.api.query.PostReadQueryApi;
import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import com.nowcoder.community.user.service.UserSocialProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetUserProfilePageQueryTest {

    @Test
    void getShouldKeepAnonymousViewerBehaviorForProfilePage() {
        UserProfileQueryApi userProfileQueryApi = mock(UserProfileQueryApi.class);
        UserSocialProfileService userSocialProfileService = mock(UserSocialProfileService.class);
        PostReadQueryApi postReadQueryApi = mock(PostReadQueryApi.class);
        GetUserProfilePageQuery query = new GetUserProfilePageQuery(
                userProfileQueryApi,
                userSocialProfileService,
                postReadQueryApi
        );
        Date createTime = new Date();
        when(userProfileQueryApi.getProfile(7))
                .thenReturn(new UserProfileView(7, "alice", "h7", 2, 0, createTime, 250, 3));
        UserSocialProfileService.UserProfileStats stats = new UserSocialProfileService.UserProfileStats();
        stats.setLikeCount(12);
        stats.setFolloweeCount(5);
        stats.setFollowerCount(8);
        stats.setHasFollowed(true);
        when(userSocialProfileService.userProfileStats(7, 0)).thenReturn(stats);

        UserProfilePageView page = query.get(null, 7);

        assertThat(page.userId()).isEqualTo(7);
        assertThat(page.username()).isEqualTo("alice");
        assertThat(page.headerUrl()).isEqualTo("h7");
        assertThat(page.type()).isEqualTo(2);
        assertThat(page.status()).isEqualTo(0);
        assertThat(page.createTime()).isEqualTo(createTime);
        assertThat(page.score()).isEqualTo(250);
        assertThat(page.level()).isEqualTo(3);
        assertThat(page.likeCount()).isEqualTo(12);
        assertThat(page.followeeCount()).isEqualTo(5);
        assertThat(page.followerCount()).isEqualTo(8);
        assertThat(page.hasFollowed()).isFalse();
        assertThat(page.socialDegraded()).isFalse();
        verify(userProfileQueryApi).getProfile(7);
        verify(userSocialProfileService).userProfileStats(7, 0);
    }

    @Test
    void getShouldKeepLoggedInViewerBehaviorForAnotherUsersProfilePage() {
        UserProfileQueryApi userProfileQueryApi = mock(UserProfileQueryApi.class);
        UserSocialProfileService userSocialProfileService = mock(UserSocialProfileService.class);
        PostReadQueryApi postReadQueryApi = mock(PostReadQueryApi.class);
        GetUserProfilePageQuery query = new GetUserProfilePageQuery(
                userProfileQueryApi,
                userSocialProfileService,
                postReadQueryApi
        );
        Date createTime = new Date();
        when(userProfileQueryApi.getProfile(7))
                .thenReturn(new UserProfileView(7, "alice", "h7", 2, 0, createTime, 250, 3));
        UserSocialProfileService.UserProfileStats stats = new UserSocialProfileService.UserProfileStats();
        stats.setLikeCount(12);
        stats.setFolloweeCount(5);
        stats.setFollowerCount(8);
        stats.setHasFollowed(true);
        when(userSocialProfileService.userProfileStats(7, 42)).thenReturn(stats);

        UserProfilePageView page = query.get(authentication(42), 7);

        assertThat(page.userId()).isEqualTo(7);
        assertThat(page.hasFollowed()).isTrue();
        verify(userProfileQueryApi).getProfile(7);
        verify(userSocialProfileService).userProfileStats(7, 42);
    }

    @Test
    void listRecentPostsShouldVerifyUserAndProjectOwnerDomainItems() {
        UserProfileQueryApi userProfileQueryApi = mock(UserProfileQueryApi.class);
        UserSocialProfileService userSocialProfileService = mock(UserSocialProfileService.class);
        PostReadQueryApi postReadQueryApi = mock(PostReadQueryApi.class);
        GetUserProfilePageQuery query = new GetUserProfilePageQuery(
                userProfileQueryApi,
                userSocialProfileService,
                postReadQueryApi
        );
        Date createTime = new Date();
        Date lastReplyTime = new Date(createTime.getTime() + 1_000);
        Date lastActivityTime = new Date(createTime.getTime() + 2_000);
        when(userProfileQueryApi.getProfile(7))
                .thenReturn(new UserProfileView(7, "alice", "h7", 2, 0, createTime, 250, 3));
        when(postReadQueryApi.listPostsByUser(7, 1, 5)).thenReturn(List.of(new PostSummaryView(
                11,
                7,
                "first post",
                1,
                0,
                createTime,
                4,
                9.5,
                3,
                List.of("java", "spring"),
                8,
                lastReplyTime,
                lastActivityTime,
                "latest reply"
        )));

        List<UserProfilePageView.RecentPostSummaryView> items = query.listRecentPosts(7, 1, 5);

        assertThat(items).hasSize(1);
        UserProfilePageView.RecentPostSummaryView item = items.get(0);
        assertThat(item.id()).isEqualTo(11);
        assertThat(item.userId()).isEqualTo(7);
        assertThat(item.title()).isEqualTo("first post");
        assertThat(item.type()).isEqualTo(1);
        assertThat(item.status()).isEqualTo(0);
        assertThat(item.createTime()).isEqualTo(createTime);
        assertThat(item.commentCount()).isEqualTo(4);
        assertThat(item.score()).isEqualTo(9.5);
        assertThat(item.categoryId()).isEqualTo(3);
        assertThat(item.tags()).containsExactly("java", "spring");
        assertThat(item.lastReplyUserId()).isEqualTo(8);
        assertThat(item.lastReplyTime()).isEqualTo(lastReplyTime);
        assertThat(item.lastActivityTime()).isEqualTo(lastActivityTime);
        assertThat(item.lastReplyPreview()).isEqualTo("latest reply");
        verify(userProfileQueryApi).getProfile(7);
        verify(postReadQueryApi).listPostsByUser(7, 1, 5);
    }

    @Test
    void listRecentCommentsShouldVerifyUserAndProjectOwnerDomainItems() {
        UserProfileQueryApi userProfileQueryApi = mock(UserProfileQueryApi.class);
        UserSocialProfileService userSocialProfileService = mock(UserSocialProfileService.class);
        PostReadQueryApi postReadQueryApi = mock(PostReadQueryApi.class);
        GetUserProfilePageQuery query = new GetUserProfilePageQuery(
                userProfileQueryApi,
                userSocialProfileService,
                postReadQueryApi
        );
        Date createTime = new Date();
        when(userProfileQueryApi.getProfile(7))
                .thenReturn(new UserProfileView(7, "alice", "h7", 2, 0, createTime, 250, 3));
        when(postReadQueryApi.listRecentCommentsByUser(7, 2, 10)).thenReturn(List.of(new RecentUserCommentView(
                21,
                7,
                1,
                101,
                0,
                201,
                "post title",
                "reply body",
                createTime
        )));

        List<UserProfilePageView.RecentCommentItemView> items = query.listRecentComments(7, 2, 10);

        assertThat(items).hasSize(1);
        UserProfilePageView.RecentCommentItemView item = items.get(0);
        assertThat(item.id()).isEqualTo(21);
        assertThat(item.userId()).isEqualTo(7);
        assertThat(item.entityType()).isEqualTo(1);
        assertThat(item.entityId()).isEqualTo(101);
        assertThat(item.targetId()).isEqualTo(0);
        assertThat(item.postId()).isEqualTo(201);
        assertThat(item.postTitle()).isEqualTo("post title");
        assertThat(item.content()).isEqualTo("reply body");
        assertThat(item.createTime()).isEqualTo(createTime);
        verify(userProfileQueryApi).getProfile(7);
        verify(postReadQueryApi).listRecentCommentsByUser(7, 2, 10);
    }

    private Authentication authentication(int userId) {
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(String.valueOf(userId))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        return authentication;
    }
}
