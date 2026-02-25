package com.nowcoder.community.user.api;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.user.api.dto.AdminUserResponse;
import com.nowcoder.community.user.api.dto.UpdateUserRoleRequest;
import com.nowcoder.community.user.dao.UserMapper;
import com.nowcoder.community.user.entity.User;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.nowcoder.community.contracts.api.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.contracts.api.CommonErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.contracts.api.CommonErrorCode.UNAUTHORIZED;

@RestController
@RequestMapping("/api/users/admin")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    private final UserMapper userMapper;

    public AdminUserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/search")
    public Result<AdminUserResponse> search(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email
    ) {
        User user;
        if (userId != null && userId > 0) {
            user = userMapper.selectById(userId);
        } else if (StringUtils.hasText(username)) {
            user = userMapper.selectByName(username.trim());
        } else if (StringUtils.hasText(email)) {
            user = userMapper.selectByEmail(email.trim());
        } else {
            throw new BusinessException(INVALID_ARGUMENT, "请提供 userId/username/email 之一");
        }

        if (user == null || user.getId() <= 0) {
            return Result.ok(null);
        }

        AdminUserResponse resp = new AdminUserResponse();
        resp.setId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setEmail(user.getEmail());
        resp.setType(user.getType());
        resp.setStatus(user.getStatus());
        resp.setHeaderUrl(user.getHeaderUrl());
        resp.setCreateTime(user.getCreateTime());
        return Result.ok(resp);
    }

    @PostMapping("/role")
    public Result<Void> updateRole(Authentication authentication, @Valid @RequestBody UpdateUserRoleRequest request) {
        int actorUserId = currentUserId(authentication);
        if (!request.isConfirm()) {
            throw new BusinessException(INVALID_ARGUMENT, "需要二次确认（confirm=true）");
        }
        int targetUserId = request.getTargetUserId();
        int toType = request.getType();
        String reason = StringUtils.hasText(request.getReason()) ? request.getReason().trim() : "";
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(INVALID_ARGUMENT, "reason 不能为空");
        }

        User target = userMapper.selectById(targetUserId);
        if (target == null || target.getId() <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "目标用户不存在");
        }

        int fromType = target.getType();
        if (actorUserId == targetUserId && toType != 1) {
            // 防止误操作导致“最后一个管理员自降级”而锁死管理入口
            throw new BusinessException(FORBIDDEN, "不允许降级自己的管理员权限");
        }

        if (fromType == toType) {
            return Result.ok();
        }

        int updated = userMapper.updateType(targetUserId, toType);
        if (updated <= 0) {
            throw new BusinessException(INTERNAL_ERROR, "更新用户角色失败");
        }

        log.info("[audit] action=admin_user_role_update actorUserId={} targetUserId={} fromType={} toType={} reason={}",
                actorUserId, targetUserId, fromType, toType, reason);
        return Result.ok();
    }

    private int currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(UNAUTHORIZED, "未获取到认证信息");
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String sub = jwt.getSubject();
        try {
            return Integer.parseInt(sub);
        } catch (NumberFormatException e) {
            throw new BusinessException(INVALID_ARGUMENT, "token subject 非法");
        }
    }
}
