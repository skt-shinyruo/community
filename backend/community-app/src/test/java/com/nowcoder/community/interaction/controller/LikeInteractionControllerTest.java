package com.nowcoder.community.interaction.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.interaction.application.LikeInteractionApplicationService;
import com.nowcoder.community.interaction.application.command.SetLikeInteractionCommand;
import com.nowcoder.community.interaction.application.result.LikeInteractionResult;
import com.nowcoder.community.interaction.controller.dto.LikeRequest;
import com.nowcoder.community.interaction.controller.dto.LikeResponse;
import com.nowcoder.community.social.controller.LikeController;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeInteractionControllerTest {

    @Test
    void postLikeRouteShouldBelongToInteractionAndEnterOnlyItsApplicationService() throws Exception {
        assertThat(LikeInteractionController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/likes");
        assertThat(LikeInteractionController.class
                .getDeclaredMethod("setLike", Authentication.class, LikeRequest.class)
                .getAnnotation(PostMapping.class))
                .isNotNull();
        assertThat(Arrays.stream(LikeInteractionController.class.getDeclaredFields())
                .map(Field::getType)
                .toList())
                .containsExactly(LikeInteractionApplicationService.class);
    }

    @Test
    void socialReadControllerShouldNoLongerOwnThePostLikeRoute() {
        assertThat(Arrays.stream(LikeController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .toList())
                .isEmpty();
    }

    @Test
    void setLikeShouldPreserveRequestAndResponseWhileDelegatingToInteraction() {
        LikeInteractionApplicationService applicationService = mock(LikeInteractionApplicationService.class);
        LikeInteractionController controller = new LikeInteractionController(applicationService);
        UUID actorUserId = uuid(7);
        UUID postId = uuid(11);
        LikeRequest request = new LikeRequest();
        request.setEntityType(POST);
        request.setEntityId(postId);
        request.setLiked(Boolean.TRUE);
        when(applicationService.setLike(new SetLikeInteractionCommand(actorUserId, POST, postId, true)))
                .thenReturn(new LikeInteractionResult(true, 3L));

        Result<LikeResponse> result = controller.setLike(authentication(actorUserId), request);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData().isLiked()).isTrue();
        assertThat(result.getData().getLikeCount()).isEqualTo(3L);
        verify(applicationService).setLike(new SetLikeInteractionCommand(actorUserId, POST, postId, true));
    }

    private Authentication authentication(UUID userId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
        return new TestingAuthenticationToken(jwt, null);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
