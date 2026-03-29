package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.dto.AdminUserResponse;
import com.nowcoder.community.user.dto.UpdateUserRoleRequest;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final UserMapper userMapper;

    public AdminUserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public AdminUserResponse search(Integer userId, String username, String email) {
        User user = resolveSearchTarget(userId, username, email);
        if (user == null || user.getId() <= 0) {
            return null;
        }
        return toAdminUserResponse(user);
    }

    @Transactional
    public void updateRole(int actorUserId, UpdateUserRoleRequest request) {
        if (request == null) {
            throw new BusinessException(INVALID_ARGUMENT, "request 不能为空");
        }
        if (!request.isConfirm()) {
            throw new BusinessException(INVALID_ARGUMENT, "需要二次确认（confirm=true）");
        }
        String reason = StringUtils.hasText(request.getReason()) ? request.getReason().trim() : "";
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(INVALID_ARGUMENT, "reason 不能为空");
        }

        int targetUserId = request.getTargetUserId();
        int toType = request.getType();
        User target = userMapper.selectById(targetUserId);
        if (target == null || target.getId() <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "目标用户不存在");
        }

        int fromType = target.getType();
        if (actorUserId == targetUserId && toType != 1) {
            throw new BusinessException(FORBIDDEN, "不允许降级自己的管理员权限");
        }
        if (fromType == toType) {
            return;
        }

        int updated = userMapper.updateType(targetUserId, toType);
        if (updated <= 0) {
            throw new BusinessException(INTERNAL_ERROR, "更新用户角色失败");
        }

        log.info("[audit] action=admin_user_role_update actorUserId={} targetUserId={} fromType={} toType={} reason={}",
                actorUserId, targetUserId, fromType, toType, reason);
    }

    private User resolveSearchTarget(Integer userId, String username, String email) {
        if (userId != null && userId > 0) {
            return userMapper.selectById(userId);
        }
        if (StringUtils.hasText(username)) {
            return userMapper.selectByName(username.trim());
        }
        if (StringUtils.hasText(email)) {
            return userMapper.selectByEmail(email.trim());
        }
        throw new BusinessException(INVALID_ARGUMENT, "请提供 userId/username/email 之一");
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        AdminUserResponse response = new AdminUserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setType(user.getType());
        response.setStatus(user.getStatus());
        response.setHeaderUrl(user.getHeaderUrl());
        response.setCreateTime(user.getCreateTime());
        return response;
    }
}
