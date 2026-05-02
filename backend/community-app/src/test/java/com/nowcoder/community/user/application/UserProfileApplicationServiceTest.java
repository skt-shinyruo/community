package com.nowcoder.community.user.application;

import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.api.model.RecentUserCommentView;
import com.nowcoder.community.content.api.query.PostReadQueryApi;
import com.nowcoder.community.growth.api.model.UserLevelSummaryView;
import com.nowcoder.community.growth.api.query.UserLevelQueryApi;
import com.nowcoder.community.social.api.query.SocialFollowQueryApi;
import com.nowcoder.community.social.api.query.SocialLikeQueryApi;
import com.nowcoder.community.user.application.result.UserProfilePageResult;
import com.nowcoder.community.user.application.result.UserProfileResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileApplicationServiceTest {

    @Mock
    private UserReadApplicationService userReadApplicationService;

    @Mock
    private SocialLikeQueryApi socialLikeQueryApi;

    @Mock
    private SocialFollowQueryApi socialFollowQueryApi;

    @Mock
    private PostReadQueryApi postReadQueryApi;

    @Mock
    private UserLevelQueryApi userLevelQueryApi;

    @Test
    void getShouldAssembleProfilePageFromApplicationAndForeignApis() {
        UserProfileApplicationService service = new UserProfileApplicationService(
                userReadApplicationService,
                socialLikeQueryApi,
                socialFollowQueryApi,
                postReadQueryApi,
                userLevelQueryApi
        );
        UUID userId = uuid(7);
        UUID viewerId = uuid(42);
        Date createTime = new Date();
        when(userReadApplicationService.getProfile(userId))
                .thenReturn(new UserProfileResult(userId, "alice", "h7", 2, 1, createTime, 250, 3, 900L, "ACTIVE"));
        when(socialLikeQueryApi.userLikeCount(userId)).thenReturn(12L);
        when(socialFollowQueryApi.followeeCount(userId, USER)).thenReturn(5L);
        when(socialFollowQueryApi.followerCount(USER, userId)).thenReturn(8L);
        when(socialFollowQueryApi.hasFollowed(viewerId, USER, userId)).thenReturn(true);
        when(userLevelQueryApi.evaluateLevel(userId)).thenReturn(new UserLevelSummaryView(4, 13, 30, 7, 14, true));

        UserProfilePageResult page = service.get(viewerId, userId);

        assertThat(page).extracting(
                UserProfilePageResult::userId,
                UserProfilePageResult::username,
                UserProfilePageResult::userLevelEnabled,
                UserProfilePageResult::userLevel,
                UserProfilePageResult::signInDaysInWindow,
                UserProfilePageResult::likeCount,
                UserProfilePageResult::followeeCount,
                UserProfilePageResult::followerCount,
                UserProfilePageResult::hasFollowed,
                UserProfilePageResult::socialDegraded
        ).containsExactly(userId, "alice", true, 4, 13, 12L, 5L, 8L, true, false);
    }

    @Test
    void getShouldNotCheckFollowedWhenViewerIsAnonymousOrSelf() {
        UserProfileApplicationService service = new UserProfileApplicationService(
                userReadApplicationService,
                socialLikeQueryApi,
                socialFollowQueryApi,
                postReadQueryApi,
                userLevelQueryApi
        );
        UUID userId = uuid(7);
        when(userReadApplicationService.getProfile(userId))
                .thenReturn(new UserProfileResult(userId, "alice", "h7", 2, 1, new Date(), 250, 3, 900L, "ACTIVE"));
        when(userLevelQueryApi.evaluateLevel(userId)).thenReturn(null);

        service.get(null, userId);
        service.get(userId, userId);

        verify(socialFollowQueryApi, never()).hasFollowed(userId, USER, userId);
    }

    @Test
    void listRecentPostsShouldRequireExistingUserAndMapForeignPostFields() {
        UserProfileApplicationService service = new UserProfileApplicationService(
                userReadApplicationService,
                socialLikeQueryApi,
                socialFollowQueryApi,
                postReadQueryApi,
                userLevelQueryApi
        );
        UUID userId = uuid(7);
        UUID postId = uuid(11);
        UUID categoryId = uuid(3);
        UUID lastReplyUserId = uuid(8);
        Date createTime = new Date();
        Date lastReplyTime = new Date(createTime.getTime() + 1_000);
        Date lastActivityTime = new Date(createTime.getTime() + 2_000);
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

        List<UserProfilePageResult.RecentPostSummaryResult> items = service.listRecentPosts(userId, 1, 5);

        assertThat(items).containsExactly(new UserProfilePageResult.RecentPostSummaryResult(
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
    }

    @Test
    void listRecentCommentsShouldRequireExistingUserAndMapForeignCommentFields() {
        UserProfileApplicationService service = new UserProfileApplicationService(
                userReadApplicationService,
                socialLikeQueryApi,
                socialFollowQueryApi,
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

        List<UserProfilePageResult.RecentCommentItemResult> items = service.listRecentComments(userId, 2, 10);

        assertThat(items).containsExactly(new UserProfilePageResult.RecentCommentItemResult(
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
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
