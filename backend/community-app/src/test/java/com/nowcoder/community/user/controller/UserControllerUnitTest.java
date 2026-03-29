package com.nowcoder.community.user.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import com.nowcoder.community.user.dto.BatchUserSummaryRequest;
import com.nowcoder.community.user.dto.UserProfileResponse;
import com.nowcoder.community.user.dto.UserResolveResponse;
import com.nowcoder.community.user.dto.UserSummaryResponse;
import com.nowcoder.community.user.service.AvatarService;
import com.nowcoder.community.user.service.UserService;
import com.nowcoder.community.user.service.UserSocialProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    private UserProfileQueryApi userProfileQueryApi;

    @Mock
    private UserService userService;

    @Mock
    private AvatarService avatarService;

    @Mock
    private UserSocialProfileService userSocialProfileService;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(
                userLookupQueryApi,
                userProfileQueryApi,
                userService,
                avatarService,
                userSocialProfileService,
                org.mockito.Mockito.mock(com.nowcoder.community.content.api.query.PostReadQueryApi.class)
        );
    }

    @Test
    void getUserShouldProjectOwnerDomainProfileAndSocialStats() {
        Date createTime = new Date();
        when(userProfileQueryApi.getProfile(7))
                .thenReturn(new UserProfileView(7, "alice", "h7", 2, 0, createTime, 250, 3));
        UserSocialProfileService.UserProfileStats stats = new UserSocialProfileService.UserProfileStats();
        stats.setLikeCount(12);
        stats.setFolloweeCount(5);
        stats.setFollowerCount(8);
        stats.setHasFollowed(true);
        when(userSocialProfileService.userProfileStats(7, 42)).thenReturn(stats);

        Result<UserProfileResponse> result = controller.getUser(authentication(42), 7);

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
        verify(userProfileQueryApi).getProfile(7);
        verify(userSocialProfileService).userProfileStats(7, 42);
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
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        return authentication;
    }
}
