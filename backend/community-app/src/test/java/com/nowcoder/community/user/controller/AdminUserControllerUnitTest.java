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
        AdminUserResponse response = new AdminUserResponse();
        response.setId(7);
        response.setUsername("alice");
        when(adminUserService.search(7, null, null)).thenReturn(response);

        Result<AdminUserResponse> result = controller.search(7, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isSameAs(response);
        verify(adminUserService).search(7, null, null);
    }

    @Test
    void updateRoleShouldResolveActorUserIdAndDelegate() {
        UpdateUserRoleRequest request = new UpdateUserRoleRequest();
        request.setTargetUserId(8);
        request.setType(2);
        request.setReason("delegate moderation");
        request.setConfirm(true);

        Result<Void> result = controller.updateRole(authentication(99), request);

        assertThat(result.getCode()).isEqualTo(0);
        verify(adminUserService).updateRole(99, request);
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
