package com.nowcoder.community.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.user.application.UserAvatarApplicationService;
import com.nowcoder.community.user.application.UserProfileApplicationService;
import com.nowcoder.community.user.application.UserReadApplicationService;
import com.nowcoder.community.user.application.result.UserProfilePageResult;
import com.nowcoder.community.user.application.result.UserSummaryResult;
import com.nowcoder.community.user.controller.dto.BatchUserSummaryRequest;
import com.nowcoder.community.user.controller.dto.UserProfilePostSummaryResponse;
import com.nowcoder.community.user.controller.dto.UserProfileResponse;
import com.nowcoder.community.user.controller.dto.UserRecentCommentItemResponse;
import com.nowcoder.community.user.controller.dto.UserSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerUnitTest {

    @Mock
    private UserReadApplicationService userReadApplicationService;

    @Mock
    private UserProfileApplicationService userProfileApplicationService;

    @Mock
    private UserAvatarApplicationService userAvatarApplicationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(
                userReadApplicationService,
                userProfileApplicationService,
                userAvatarApplicationService
        );
    }

    @Test
    void getUserShouldDelegateProfileAssemblyToUserOwnedQuery() throws Exception {
        UUID actorUserId = uuid(42);
        UUID userId = uuid(7);
        Authentication authentication = authentication(actorUserId);
        Date createTime = new Date();
        when(userProfileApplicationService.get(actorUserId, userId))
                .thenReturn(new UserProfilePageResult(userId, "alice", "h7", 2, 0, createTime, 250, 3, true, 2, 13, 12, 5, 8, true));

        Result<UserProfileResponse> result = controller.getUser(authentication, userId);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getId()).isEqualTo(userId);
        assertThat(result.getData().getUsername()).isEqualTo("alice");
        assertThat(result.getData().getHeaderUrl()).isEqualTo("h7");
        assertThat(result.getData().getType()).isEqualTo(2);
        assertThat(result.getData().getStatus()).isEqualTo(0);
        assertThat(result.getData().getCreateTime()).isEqualTo(createTime);
        assertThat(result.getData().getScore()).isEqualTo(250);
        assertThat(result.getData().getLevel()).isEqualTo(3);
        assertThat(result.getData().isUserLevelEnabled()).isTrue();
        assertThat(result.getData()).extracting("userLevel", "signInDaysInWindow")
                .containsExactly(2, 13);
        JsonNode data = objectMapper.valueToTree(result).path("data");
        assertThat(data.has("walletBalance")).isFalse();
        assertThat(data.has("walletStatus")).isFalse();
        assertThat(result.getData().getLikeCount()).isEqualTo(12);
        assertThat(result.getData().getFolloweeCount()).isEqualTo(5);
        assertThat(result.getData().getFollowerCount()).isEqualTo(8);
        assertThat(result.getData().getHasFollowed()).isTrue();
        verify(userProfileApplicationService).get(actorUserId, userId);
    }

    @Test
    void getUserShouldHideNewUserLevelFieldsWhenFeatureDisabled() throws Exception {
        UUID actorUserId = uuid(42);
        UUID userId = uuid(8);
        Authentication authentication = authentication(actorUserId);
        Date createTime = new Date();
        when(userProfileApplicationService.get(actorUserId, userId))
                .thenReturn(new UserProfilePageResult(userId, "bob", "h8", 1, 0, createTime, 99, 2, false, null, null, 3, 4, 5, false));

        Result<UserProfileResponse> result = controller.getUser(authentication, userId);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().isUserLevelEnabled()).isFalse();
        assertThat(result.getData().getUserLevel()).isNull();
        assertThat(result.getData().getSignInDaysInWindow()).isNull();
        assertThat(result.getData().getLevel()).isEqualTo(2);
        assertThat(result.getData().getScore()).isEqualTo(99);
        JsonNode data = objectMapper.valueToTree(result).path("data");
        assertThat(data.has("walletBalance")).isFalse();
        assertThat(data.has("walletStatus")).isFalse();
        verify(userProfileApplicationService).get(actorUserId, userId);
    }

    @Test
    void recentPostsShouldReturnUserOwnedPostSummaryResponses() {
        UUID userId = uuid(7);
        UUID postId = uuid(11);
        UUID categoryId = uuid(3);
        UUID lastReplyUserId = uuid(8);
        Date createTime = new Date();
        Date lastReplyTime = new Date(createTime.getTime() + 1_000);
        Date lastActivityTime = new Date(createTime.getTime() + 2_000);
        when(userProfileApplicationService.listRecentPosts(userId, 1, 5))
                .thenReturn(List.of(new UserProfilePageResult.RecentPostSummaryResult(
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

        Result<List<UserProfilePostSummaryResponse>> result = controller.recentPosts(userId, 1, 5);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).hasSize(1);
        UserProfilePostSummaryResponse item = result.getData().get(0);
        assertThat(item.getId()).isEqualTo(postId);
        assertThat(item.getUserId()).isEqualTo(userId);
        assertThat(item.getTitle()).isEqualTo("first post");
        assertThat(item.getType()).isEqualTo(1);
        assertThat(item.getStatus()).isEqualTo(0);
        assertThat(item.getCreateTime()).isEqualTo(createTime);
        assertThat(item.getCommentCount()).isEqualTo(4);
        assertThat(item.getScore()).isEqualTo(9.5);
        assertThat(item.getCategoryId()).isEqualTo(categoryId);
        assertThat(item.getTags()).containsExactly("java", "spring");
        assertThat(item.getLastReplyUserId()).isEqualTo(lastReplyUserId);
        assertThat(item.getLastReplyTime()).isEqualTo(lastReplyTime);
        assertThat(item.getLastActivityTime()).isEqualTo(lastActivityTime);
        assertThat(item.getLastReplyPreview()).isEqualTo("latest reply");
        verify(userProfileApplicationService).listRecentPosts(userId, 1, 5);
    }

    @Test
    void recentCommentsShouldReturnUserOwnedRecentCommentResponses() {
        UUID userId = uuid(7);
        UUID commentId = uuid(21);
        UUID entityId = uuid(101);
        UUID targetId = uuid(301);
        UUID postId = uuid(201);
        Date createTime = new Date();
        when(userProfileApplicationService.listRecentComments(userId, 2, 10))
                .thenReturn(List.of(new UserProfilePageResult.RecentCommentItemResult(
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

        Result<List<UserRecentCommentItemResponse>> result = controller.recentComments(userId, 2, 10);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).hasSize(1);
        UserRecentCommentItemResponse item = result.getData().get(0);
        assertThat(item.getId()).isEqualTo(commentId);
        assertThat(item.getUserId()).isEqualTo(userId);
        assertThat(item.getEntityType()).isEqualTo(1);
        assertThat(item.getEntityId()).isEqualTo(entityId);
        assertThat(item.getTargetId()).isEqualTo(targetId);
        assertThat(item.getPostId()).isEqualTo(postId);
        assertThat(item.getPostTitle()).isEqualTo("post title");
        assertThat(item.getContent()).isEqualTo("reply body");
        assertThat(item.getCreateTime()).isEqualTo(createTime);
        verify(userProfileApplicationService).listRecentComments(userId, 2, 10);
    }

    @Test
    void batchSummaryShouldPreserveRequestOrderUsingApplicationServiceResponses() {
        UUID aliceId = uuid(7);
        UUID bobId = uuid(9);
        BatchUserSummaryRequest request = new BatchUserSummaryRequest();
        request.setUserIds(Arrays.asList(aliceId, bobId, aliceId, null));
        when(userReadApplicationService.listSummaryResultsByIds(Arrays.asList(aliceId, bobId, aliceId, null)))
                .thenReturn(List.of(
                        new UserSummaryResult(aliceId, "alice", "h7", 1),
                        new UserSummaryResult(bobId, "bob", "h9", 2)
                ));

        Result<List<UserSummaryResponse>> result = controller.batchSummary(request);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).extracting(UserSummaryResponse::getId).containsExactly(aliceId, bobId);
        assertThat(result.getData()).extracting(UserSummaryResponse::getUsername).containsExactly("alice", "bob");
        verify(userReadApplicationService).listSummaryResultsByIds(Arrays.asList(aliceId, bobId, aliceId, null));
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
