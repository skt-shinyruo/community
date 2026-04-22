package com.nowcoder.community.user.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.user.dto.AdminUserResponse;
import com.nowcoder.community.user.dto.UpdateUserRoleRequest;
import com.nowcoder.community.user.service.AdminUserService;
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
    private AdminUserService adminUserService;

    private AdminUserController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminUserController(adminUserService);
    }

    @Test
    void searchShouldDelegateToAdminUserService() {
        UUID userId = uuid(7);
        AdminUserResponse response = new AdminUserResponse();
        response.setId(userId);
        response.setUsername("alice");
        when(adminUserService.search(userId, null, null)).thenReturn(response);

        Result<AdminUserResponse> result = controller.search(userId, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isSameAs(response);
        verify(adminUserService).search(userId, null, null);
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
        verify(adminUserService).updateRole(actorUserId, request);
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
