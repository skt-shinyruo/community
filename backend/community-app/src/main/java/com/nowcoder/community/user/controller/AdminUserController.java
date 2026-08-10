package com.nowcoder.community.user.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.user.application.AdminUserApplicationService;
import com.nowcoder.community.user.controller.dto.UpdateUserRoleRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/admin")
public class AdminUserController {

    private final AdminUserApplicationService adminUserApplicationService;

    public AdminUserController(AdminUserApplicationService adminUserApplicationService) {
        this.adminUserApplicationService = Objects.requireNonNull(
                adminUserApplicationService,
                "adminUserApplicationService must not be null"
        );
    }

    @GetMapping("/search")
    public Result<AdminUserApplicationService.AdminUserResult> search(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email
    ) {
        return Result.ok(adminUserApplicationService.search(userId, username, email));
    }

    @PostMapping("/role")
    public Result<Void> updateRole(Authentication authentication, @Valid @RequestBody UpdateUserRoleRequest request) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        adminUserApplicationService.updateRole(toCommand(actorUserId, request));
        return Result.ok();
    }

    private static AdminUserApplicationService.UpdateRoleCommand toCommand(
            UUID actorUserId,
            UpdateUserRoleRequest request
    ) {
        if (request == null) {
            return null;
        }
        return new AdminUserApplicationService.UpdateRoleCommand(
                actorUserId,
                request.targetUserId(),
                request.type(),
                request.reason(),
                request.confirm()
        );
    }
}
