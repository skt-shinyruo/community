package com.nowcoder.community.social.controller;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.social.application.FollowApplicationService;
import com.nowcoder.community.social.application.command.FollowCommand;
import com.nowcoder.community.social.application.command.UnfollowCommand;
import com.nowcoder.community.social.application.result.FollowRelationResult;
import com.nowcoder.community.social.controller.dto.FollowRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FollowControllerTest {

    @Test
    void followShouldDelegateToFollowApplicationService() {
        FollowApplicationService followApplicationService = mock(FollowApplicationService.class);
        FollowController controller = new FollowController(followApplicationService);
        UUID userId = uuid(7);

        FollowRequest request = new FollowRequest();
        request.setEntityType(EntityTypes.USER);
        request.setEntityId(uuid(8));

        Result<Void> result = controller.follow(authentication(userId), request);

        assertThat(result.getCode()).isEqualTo(0);
        verify(followApplicationService).follow(new FollowCommand(userId, EntityTypes.USER, uuid(8)));
    }

    @Test
    void followShouldDelegateNonUserEntityTypeToApplicationService() {
        FollowApplicationService followApplicationService = mock(FollowApplicationService.class);
        FollowController controller = new FollowController(followApplicationService);
        UUID userId = uuid(7);
        UUID targetId = uuid(8);

        FollowRequest request = new FollowRequest();
        request.setEntityType(EntityTypes.POST);
        request.setEntityId(targetId);

        Result<Void> result = controller.follow(authentication(userId), request);

        assertThat(result.getCode()).isEqualTo(0);
        verify(followApplicationService).follow(new FollowCommand(userId, EntityTypes.POST, targetId));
    }

    @Test
    void unfollowAndStatusShouldDelegateNonUserEntityTypeToApplicationService() {
        FollowApplicationService followApplicationService = mock(FollowApplicationService.class);
        FollowController controller = new FollowController(followApplicationService);
        UUID userId = uuid(7);
        UUID targetId = uuid(8);
        when(followApplicationService.hasFollowed(userId, EntityTypes.POST, targetId)).thenReturn(false);

        Result<Void> unfollowResult = controller.unfollow(authentication(userId), EntityTypes.POST, targetId);
        Result<Boolean> statusResult = controller.status(authentication(userId), EntityTypes.POST, targetId);

        assertThat(unfollowResult.getCode()).isEqualTo(0);
        assertThat(statusResult.getData()).isFalse();
        verify(followApplicationService).unfollow(new UnfollowCommand(userId, EntityTypes.POST, targetId));
        verify(followApplicationService).hasFollowed(userId, EntityTypes.POST, targetId);
    }

    @Test
    void followListAndCountEndpointsShouldDelegateSuppliedEntityType() {
        FollowApplicationService followApplicationService = mock(FollowApplicationService.class);
        FollowController controller = new FollowController(followApplicationService);
        UUID userId = uuid(7);
        UUID targetId = uuid(8);
        when(followApplicationService.listFollowees(userId, EntityTypes.POST, 1, 20))
                .thenReturn(List.of(new FollowRelationResult(targetId, Instant.EPOCH)));
        when(followApplicationService.listFollowers(EntityTypes.POST, userId, 1, 20))
                .thenReturn(List.of(new FollowRelationResult(targetId, Instant.EPOCH)));
        when(followApplicationService.followeeCount(userId, EntityTypes.POST)).thenReturn(3L);
        when(followApplicationService.followerCount(EntityTypes.POST, userId)).thenReturn(4L);

        assertThat(controller.followees(userId, EntityTypes.POST, 1, 20).getData())
                .extracting("targetId")
                .containsExactly(targetId);
        assertThat(controller.followers(userId, EntityTypes.POST, 1, 20).getData())
                .extracting("targetId")
                .containsExactly(targetId);
        assertThat(controller.followeeCount(userId, EntityTypes.POST).getData()).isEqualTo(3L);
        assertThat(controller.followerCount(userId, EntityTypes.POST).getData()).isEqualTo(4L);
        verify(followApplicationService).listFollowees(userId, EntityTypes.POST, 1, 20);
        verify(followApplicationService).listFollowers(EntityTypes.POST, userId, 1, 20);
        verify(followApplicationService).followeeCount(userId, EntityTypes.POST);
        verify(followApplicationService).followerCount(EntityTypes.POST, userId);
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
}
