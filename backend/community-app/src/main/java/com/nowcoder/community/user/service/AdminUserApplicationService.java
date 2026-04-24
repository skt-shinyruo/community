package com.nowcoder.community.user.service;

import com.nowcoder.community.user.dto.AdminUserResponse;
import com.nowcoder.community.user.dto.UpdateUserRoleRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AdminUserApplicationService {

    private final AdminUserService adminUserService;

    public AdminUserApplicationService(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    public AdminUserResponse search(UUID userId, String username, String email) {
        return adminUserService.search(userId, username, email);
    }

    public void updateRole(UUID actorUserId, UpdateUserRoleRequest request) {
        adminUserService.updateRole(actorUserId, request);
    }
}
