package com.nowcoder.community.social.follow;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.social.follow.dto.FollowRequest;
import com.nowcoder.community.social.service.FollowApplicationService;
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
        verify(followApplicationService).follow(userId, request);
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
