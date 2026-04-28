package com.nowcoder.community.social.controller;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.social.application.LikeApplicationService;
import com.nowcoder.community.social.application.command.SetLikeCommand;
import com.nowcoder.community.social.application.result.LikeResult;
import com.nowcoder.community.social.controller.dto.LikeRequest;
import com.nowcoder.community.social.controller.dto.LikeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeControllerTest {

    @Test
    void setLikeShouldDelegateToLikeApplicationService() {
        LikeApplicationService likeApplicationService = mock(LikeApplicationService.class);
        LikeController controller = new LikeController(likeApplicationService);
        UUID userId = uuid(7);

        LikeRequest request = new LikeRequest();
        request.setEntityType(EntityTypes.POST);
        request.setEntityId(uuid(11));
        request.setLiked(Boolean.TRUE);

        when(likeApplicationService.setLike(new SetLikeCommand(userId, EntityTypes.POST, uuid(11), Boolean.TRUE)))
                .thenReturn(new LikeResult(true, 3L));

        Result<LikeResponse> result = controller.setLike(authentication(userId), request);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().isLiked()).isTrue();
        assertThat(result.getData().getLikeCount()).isEqualTo(3);
        verify(likeApplicationService).setLike(new SetLikeCommand(userId, EntityTypes.POST, uuid(11), Boolean.TRUE));
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
