package com.nowcoder.community.social.like;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.social.like.dto.LikeRequest;
import com.nowcoder.community.social.like.dto.LikeResponse;
import com.nowcoder.community.social.service.LikeApplicationService;
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

        LikeResponse response = new LikeResponse();
        response.setLiked(true);
        response.setLikeCount(3L);
        when(likeApplicationService.setLike(userId, request)).thenReturn(response);

        Result<LikeResponse> result = controller.setLike(authentication(userId), request);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isSameAs(response);
        verify(likeApplicationService).setLike(userId, request);
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
