package com.nowcoder.community.user.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.user.application.AdminUserApplicationService;
import com.nowcoder.community.user.application.command.UpdateUserRoleCommand;
import com.nowcoder.community.user.application.result.AdminUserResult;
import com.nowcoder.community.user.controller.dto.AdminUserResponse;
import com.nowcoder.community.user.controller.dto.UpdateUserRoleRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/admin")
public class AdminUserController {

    private final AdminUserApplicationService adminUserApplicationService;

    public AdminUserController(AdminUserApplicationService adminUserApplicationService) {
        this.adminUserApplicationService = adminUserApplicationService;
    }

    @GetMapping("/search")
    public Result<AdminUserResponse> search(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email
    ) {
        return Result.ok(toAdminUserResponse(adminUserApplicationService.search(userId, username, email)));
    }

    @PostMapping("/role")
    public Result<Void> updateRole(Authentication authentication, @Valid @RequestBody UpdateUserRoleRequest request) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        adminUserApplicationService.updateRole(toCommand(actorUserId, request));
        return Result.ok();
    }

    private static UpdateUserRoleCommand toCommand(UUID actorUserId, UpdateUserRoleRequest request) {
        if (request == null) {
            return null;
        }
        return new UpdateUserRoleCommand(
                actorUserId,
                request.getTargetUserId(),
                request.getType(),
                request.getReason(),
                request.isConfirm()
        );
    }

    private static AdminUserResponse toAdminUserResponse(AdminUserResult user) {
        if (user == null) {
            return null;
        }
        AdminUserResponse response = new AdminUserResponse();
        response.setId(user.id());
        response.setUsername(user.username());
        response.setEmail(user.email());
        response.setType(user.type());
        response.setStatus(user.status());
        response.setHeaderUrl(user.headerUrl());
        response.setCreateTime(user.createTime());
        return response;
    }
}
