package com.nowcoder.community.user.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.user.application.AdminUserApplicationService;
import com.nowcoder.community.user.application.command.UpdateUserRoleCommand;
import com.nowcoder.community.user.application.result.AdminUserResult;
import com.nowcoder.community.user.controller.dto.AdminUserResponse;
import com.nowcoder.community.user.controller.dto.UpdateUserRoleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerUnitTest {

    @Mock
    private AdminUserApplicationService adminUserApplicationService;

    private AdminUserController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminUserController(adminUserApplicationService);
    }

    @Test
    void searchShouldDelegateToAdminUserApplicationService() {
        UUID userId = uuid(7);
        when(adminUserApplicationService.search(userId, null, null))
                .thenReturn(new AdminUserResult(userId, "alice", "alice@example.com", 2, 0, "h7", null));

        Result<AdminUserResponse> result = controller.search(userId, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getId()).isEqualTo(userId);
        assertThat(result.getData().getUsername()).isEqualTo("alice");
        assertThat(result.getData().getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getData().getType()).isEqualTo(2);
        assertThat(result.getData().getStatus()).isEqualTo(0);
        assertThat(result.getData().getHeaderUrl()).isEqualTo("h7");
        verify(adminUserApplicationService).search(userId, null, null);
    }

    @Test
    void updateRoleShouldResolveActorUserIdAndDelegate() {
        UUID targetUserId = uuid(8);
        UUID actorUserId = uuid(99);
        UpdateUserRoleRequest request = new UpdateUserRoleRequest();
        request.setTargetUserId(targetUserId);
        request.setType(2);
        request.setReason("delegate moderation");
        request.setConfirm(true);

        Result<Void> result = controller.updateRole(authentication(actorUserId), request);

        assertThat(result.getCode()).isEqualTo(0);
        verify(adminUserApplicationService).updateRole(new UpdateUserRoleCommand(
                actorUserId,
                targetUserId,
                2,
                "delegate moderation",
                true
        ));
    }

    private Authentication authentication(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        return authentication;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
