package com.nowcoder.community.social.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.social.application.BlockApplicationService;
import com.nowcoder.community.social.application.command.BlockCommand;
import com.nowcoder.community.social.controller.dto.BlockRequest;
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

class BlockControllerTest {

    @Test
    void blockShouldDelegateToBlockApplicationService() {
        BlockApplicationService blockApplicationService = mock(BlockApplicationService.class);
        BlockController controller = new BlockController(blockApplicationService);
        UUID actorId = uuid(7);

        BlockRequest request = new BlockRequest();
        request.setUserId(uuid(8));

        Result<Void> result = controller.block(authentication(actorId), request);

        assertThat(result.getCode()).isEqualTo(0);
        verify(blockApplicationService).block(new BlockCommand(actorId, uuid(8)));
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
