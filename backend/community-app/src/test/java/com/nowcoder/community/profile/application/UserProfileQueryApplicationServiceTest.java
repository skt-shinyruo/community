package com.nowcoder.community.profile.application;

import com.nowcoder.community.content.api.query.PostReadQueryApi;
import com.nowcoder.community.content.api.query.PostReadQueryApi.PostSummaryView;
import com.nowcoder.community.content.api.query.PostReadQueryApi.RecentUserCommentView;
import com.nowcoder.community.growth.api.model.UserLevelSummaryView;
import com.nowcoder.community.growth.api.query.UserLevelQueryApi;
import com.nowcoder.community.profile.application.result.UserProfilePageResult;
import com.nowcoder.community.social.api.query.SocialFollowQueryApi;
import com.nowcoder.community.social.api.query.SocialLikeQueryApi;
import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileQueryApplicationServiceTest {

    @Mock
    private UserProfileQueryApi userProfileQueryApi;
    @Mock
    private SocialLikeQueryApi socialLikeQueryApi;
    @Mock
    private SocialFollowQueryApi socialFollowQueryApi;
    @Mock
    private PostReadQueryApi postReadQueryApi;
    @Mock
    private UserLevelQueryApi userLevelQueryApi;

    @Test
    void getShouldComposeViewerIndependentOwnerApiViews() {
        UserProfileQueryApplicationService service = service();
        UUID userId = uuid(7);
        Date createTime = new Date();
        when(userProfileQueryApi.getProfile(userId))
                .thenReturn(new UserProfileView(userId, "alice", "h7", 2, 1, createTime));
        when(socialLikeQueryApi.userLikeCount(userId)).thenReturn(12L);
        when(socialFollowQueryApi.followeeCount(userId, USER)).thenReturn(5L);
        when(socialFollowQueryApi.followerCount(USER, userId)).thenReturn(8L);
        when(userLevelQueryApi.evaluateLevel(userId)).thenReturn(new UserLevelSummaryView(4, 13, 30, 7, 14, true));

        UserProfilePageResult page = service.get(userId);

        assertThat(page).extracting(
                UserProfilePageResult::id,
                UserProfilePageResult::username,
                UserProfilePageResult::userLevel,
                UserProfilePageResult::likeCount,
                UserProfilePageResult::followeeCount,
                UserProfilePageResult::followerCount
        ).containsExactly(userId, "alice", 4, 12L, 5L, 8L);
        verify(socialFollowQueryApi, never()).hasFollowed(any(), anyInt(), any());
    }

    @Test
    void listRecentPostsShouldValidateUserBeforeCallingContent() {
        UserProfileQueryApplicationService service = service();
        UUID userId = uuid(7);
        UUID postId = uuid(11);
        Date createTime = new Date();
        when(userProfileQueryApi.getProfile(userId))
                .thenReturn(new UserProfileView(userId, "alice", "h7", 2, 1, createTime));
        PostSummaryView view = new PostSummaryView(
                postId, userId, "first post", 1, 0, createTime, 4, 9.5,
                uuid(3), List.of("java"), uuid(8), createTime, createTime, "latest reply"
        );
        when(postReadQueryApi.listPostsByUser(userId, 1, 5)).thenReturn(List.of(view));

        List<PostSummaryView> items = service.listRecentPosts(userId, 1, 5);

        assertThat(items).containsExactly(view);
        InOrder order = inOrder(userProfileQueryApi, postReadQueryApi);
        order.verify(userProfileQueryApi).getProfile(userId);
        order.verify(postReadQueryApi).listPostsByUser(userId, 1, 5);
    }

    @Test
    void listRecentCommentsShouldValidateUserBeforeCallingContent() {
        UserProfileQueryApplicationService service = service();
        UUID userId = uuid(7);
        UUID commentId = uuid(21);
        Date createTime = new Date();
        when(userProfileQueryApi.getProfile(userId))
                .thenReturn(new UserProfileView(userId, "alice", "h7", 2, 1, createTime));
        RecentUserCommentView view = new RecentUserCommentView(
                commentId, userId, 1, uuid(101), uuid(301), uuid(201),
                "post title", "reply body", createTime
        );
        when(postReadQueryApi.listRecentCommentsByUser(userId, 2, 10)).thenReturn(List.of(view));

        List<RecentUserCommentView> items = service.listRecentComments(userId, 2, 10);

        assertThat(items).containsExactly(view);
        InOrder order = inOrder(userProfileQueryApi, postReadQueryApi);
        order.verify(userProfileQueryApi).getProfile(userId);
        order.verify(postReadQueryApi).listRecentCommentsByUser(userId, 2, 10);
    }

    private UserProfileQueryApplicationService service() {
        return new UserProfileQueryApplicationService(
                userProfileQueryApi,
                socialLikeQueryApi,
                socialFollowQueryApi,
                postReadQueryApi,
                userLevelQueryApi
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
