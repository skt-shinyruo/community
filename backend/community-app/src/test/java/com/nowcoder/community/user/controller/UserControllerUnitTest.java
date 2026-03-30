package com.nowcoder.community.user.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.user.app.query.GetUserProfilePageQuery;
import com.nowcoder.community.user.app.query.UserProfilePageView;
import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.dto.BatchUserSummaryRequest;
import com.nowcoder.community.user.dto.UserProfilePostSummaryResponse;
import com.nowcoder.community.user.dto.UserProfileResponse;
import com.nowcoder.community.user.dto.UserRecentCommentItemResponse;
import com.nowcoder.community.user.dto.UserResolveResponse;
import com.nowcoder.community.user.dto.UserSummaryResponse;
import com.nowcoder.community.user.service.AvatarService;
import com.nowcoder.community.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerUnitTest {

    @Mock
    private UserLookupQueryApi userLookupQueryApi;

    @Mock
    private GetUserProfilePageQuery getUserProfilePageQuery;

    @Mock
    private UserService userService;

    @Mock
    private AvatarService avatarService;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(
                userLookupQueryApi,
                getUserProfilePageQuery,
                userService,
                avatarService
        );
    }

    @Test
    void getUserShouldDelegateProfileAssemblyToUserOwnedQuery() {
        Authentication authentication = authentication(42);
        Date createTime = new Date();
        when(getUserProfilePageQuery.get(authentication, 7))
                .thenReturn(new UserProfilePageView(7, "alice", "h7", 2, 0, createTime, 250, 3, 12, 5, 8, true, false));

        Result<UserProfileResponse> result = controller.getUser(authentication, 7);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getId()).isEqualTo(7);
        assertThat(result.getData().getUsername()).isEqualTo("alice");
        assertThat(result.getData().getHeaderUrl()).isEqualTo("h7");
        assertThat(result.getData().getType()).isEqualTo(2);
        assertThat(result.getData().getStatus()).isEqualTo(0);
        assertThat(result.getData().getCreateTime()).isEqualTo(createTime);
        assertThat(result.getData().getScore()).isEqualTo(250);
        assertThat(result.getData().getLevel()).isEqualTo(3);
        assertThat(result.getData().getLikeCount()).isEqualTo(12);
        assertThat(result.getData().getFolloweeCount()).isEqualTo(5);
        assertThat(result.getData().getFollowerCount()).isEqualTo(8);
        assertThat(result.getData().getHasFollowed()).isTrue();
        assertThat(result.getData().isSocialDegraded()).isFalse();
        verify(getUserProfilePageQuery).get(authentication, 7);
    }

    @Test
    void recentPostsShouldReturnUserOwnedPostSummaryResponses() {
        Date createTime = new Date();
        Date lastReplyTime = new Date(createTime.getTime() + 1_000);
        Date lastActivityTime = new Date(createTime.getTime() + 2_000);
        when(getUserProfilePageQuery.listRecentPosts(7, 1, 5))
                .thenReturn(List.of(new UserProfilePageView.RecentPostSummaryView(
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

        Result<List<UserProfilePostSummaryResponse>> result = controller.recentPosts(7, 1, 5);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).hasSize(1);
        UserProfilePostSummaryResponse item = result.getData().get(0);
        assertThat(item.getId()).isEqualTo(11);
        assertThat(item.getUserId()).isEqualTo(7);
        assertThat(item.getTitle()).isEqualTo("first post");
        assertThat(item.getType()).isEqualTo(1);
        assertThat(item.getStatus()).isEqualTo(0);
        assertThat(item.getCreateTime()).isEqualTo(createTime);
        assertThat(item.getCommentCount()).isEqualTo(4);
        assertThat(item.getScore()).isEqualTo(9.5);
        assertThat(item.getCategoryId()).isEqualTo(3);
        assertThat(item.getTags()).containsExactly("java", "spring");
        assertThat(item.getLastReplyUserId()).isEqualTo(8);
        assertThat(item.getLastReplyTime()).isEqualTo(lastReplyTime);
        assertThat(item.getLastActivityTime()).isEqualTo(lastActivityTime);
        assertThat(item.getLastReplyPreview()).isEqualTo("latest reply");
        verify(getUserProfilePageQuery).listRecentPosts(7, 1, 5);
    }

    @Test
    void recentCommentsShouldReturnUserOwnedRecentCommentResponses() {
        Date createTime = new Date();
        when(getUserProfilePageQuery.listRecentComments(7, 2, 10))
                .thenReturn(List.of(new UserProfilePageView.RecentCommentItemView(
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

        Result<List<UserRecentCommentItemResponse>> result = controller.recentComments(7, 2, 10);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).hasSize(1);
        UserRecentCommentItemResponse item = result.getData().get(0);
        assertThat(item.getId()).isEqualTo(21);
        assertThat(item.getUserId()).isEqualTo(7);
        assertThat(item.getEntityType()).isEqualTo(1);
        assertThat(item.getEntityId()).isEqualTo(101);
        assertThat(item.getTargetId()).isEqualTo(0);
        assertThat(item.getPostId()).isEqualTo(201);
        assertThat(item.getPostTitle()).isEqualTo("post title");
        assertThat(item.getContent()).isEqualTo("reply body");
        assertThat(item.getCreateTime()).isEqualTo(createTime);
        verify(getUserProfilePageQuery).listRecentComments(7, 2, 10);
    }

    @Test
    void resolveByUsernameShouldUseLookupSummaryView() {
        when(userLookupQueryApi.getSummaryByUsername("alice"))
                .thenReturn(new UserSummaryView(7, "alice", "h7", 2));

        Result<UserResolveResponse> result = controller.resolveByUsername("alice");

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getId()).isEqualTo(7);
        assertThat(result.getData().getUsername()).isEqualTo("alice");
        assertThat(result.getData().getHeaderUrl()).isEqualTo("h7");
        verify(userLookupQueryApi).getSummaryByUsername("alice");
    }

    @Test
    void batchSummaryShouldPreserveRequestOrderUsingSummaryViews() {
        BatchUserSummaryRequest request = new BatchUserSummaryRequest();
        request.setUserIds(List.of(7, 9, 7, 0));
        when(userLookupQueryApi.listSummariesByIds(List.of(7, 9)))
                .thenReturn(List.of(
                        new UserSummaryView(9, "bob", "h9", 2),
                        new UserSummaryView(7, "alice", "h7", 1)
                ));

        Result<List<UserSummaryResponse>> result = controller.batchSummary(request);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).extracting(UserSummaryResponse::getId).containsExactly(7, 9);
        assertThat(result.getData()).extracting(UserSummaryResponse::getUsername).containsExactly("alice", "bob");
        verify(userLookupQueryApi).listSummariesByIds(List.of(7, 9));
    }

    private Authentication authentication(int userId) {
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(String.valueOf(userId))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return new TestingAuthenticationToken(jwt, null);
    }
}
