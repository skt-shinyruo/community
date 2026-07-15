package com.nowcoder.community.profile.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.profile.application.UserProfileQueryApplicationService;
import com.nowcoder.community.profile.application.result.UserProfilePageResult;
import com.nowcoder.community.profile.controller.dto.UserProfilePostSummaryResponse;
import com.nowcoder.community.profile.controller.dto.UserProfileResponse;
import com.nowcoder.community.profile.controller.dto.UserRecentCommentItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    @Mock
    private UserProfileQueryApplicationService applicationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UserProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new UserProfileController(applicationService);
    }

    @Test
    void shouldKeepExistingProfileRoutesAndDependOnlyOnSameDomainApplicationService() throws Exception {
        assertThat(UserProfileController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/users");
        assertThat(mapping("getUser", Authentication.class, UUID.class)).containsExactly("/{userId}");
        assertThat(mapping("recentPosts", UUID.class, Integer.class, Integer.class))
                .containsExactly("/{userId}/recent-posts");
        assertThat(mapping("recentComments", UUID.class, Integer.class, Integer.class))
                .containsExactly("/{userId}/recent-comments");
        assertThat(List.of(UserProfileController.class.getDeclaredFields()))
                .extracting(Field::getType)
                .containsExactly(UserProfileQueryApplicationService.class);
    }

    @Test
    void getUserShouldPreserveProfileJsonWireWithoutWalletFields() throws Exception {
        UUID viewerId = uuid(42);
        UUID userId = uuid(7);
        Date createTime = new Date(1_720_000_000_000L);
        when(applicationService.get(viewerId, userId)).thenReturn(new UserProfilePageResult(
                userId, "alice", "h7", 2, 0, createTime, true, 2, 13, 12, 5, 8, true
        ));

        Result<UserProfileResponse> result = controller.getUser(authentication(viewerId), userId);

        JsonNode data = objectMapper.valueToTree(result).path("data");
        assertThat(data.path("id").asText()).isEqualTo(userId.toString());
        assertThat(data.path("username").asText()).isEqualTo("alice");
        assertThat(data.path("headerUrl").asText()).isEqualTo("h7");
        assertThat(data.path("userLevelEnabled").asBoolean()).isTrue();
        assertThat(data.path("userLevel").asInt()).isEqualTo(2);
        assertThat(data.path("signInDaysInWindow").asInt()).isEqualTo(13);
        assertThat(data.path("likeCount").asLong()).isEqualTo(12);
        assertThat(data.path("followeeCount").asLong()).isEqualTo(5);
        assertThat(data.path("followerCount").asLong()).isEqualTo(8);
        assertThat(data.path("hasFollowed").asBoolean()).isTrue();
        assertThat(data.has("walletBalance")).isFalse();
        assertThat(data.has("walletStatus")).isFalse();
        verify(applicationService).get(viewerId, userId);
    }

    @Test
    void recentEndpointsShouldPreserveResponseFields() {
        UUID userId = uuid(7);
        UUID postId = uuid(11);
        UUID commentId = uuid(21);
        Date createTime = new Date(1_720_000_000_000L);
        when(applicationService.listRecentPosts(userId, 1, 5)).thenReturn(List.of(
                new UserProfilePageResult.RecentPostSummaryResult(
                        postId, userId, "first post", 1, 0, createTime, 4, 9.5,
                        uuid(3), List.of("java"), uuid(8), createTime, createTime, "latest reply"
                )
        ));
        when(applicationService.listRecentComments(userId, 2, 10)).thenReturn(List.of(
                new UserProfilePageResult.RecentCommentItemResult(
                        commentId, userId, 1, uuid(101), uuid(301), uuid(201),
                        "post title", "reply body", createTime
                )
        ));

        Result<List<UserProfilePostSummaryResponse>> posts = controller.recentPosts(userId, 1, 5);
        Result<List<UserRecentCommentItemResponse>> comments = controller.recentComments(userId, 2, 10);

        assertThat(posts.getData()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(postId);
            assertThat(item.getTitle()).isEqualTo("first post");
            assertThat(item.getScore()).isEqualTo(9.5);
            assertThat(item.getTags()).containsExactly("java");
            assertThat(item.getLastReplyPreview()).isEqualTo("latest reply");
        });
        assertThat(comments.getData()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(commentId);
            assertThat(item.getPostTitle()).isEqualTo("post title");
            assertThat(item.getContent()).isEqualTo("reply body");
        });
    }

    private String[] mapping(String method, Class<?>... parameterTypes) throws Exception {
        return UserProfileController.class.getDeclaredMethod(method, parameterTypes)
                .getAnnotation(GetMapping.class)
                .value();
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
