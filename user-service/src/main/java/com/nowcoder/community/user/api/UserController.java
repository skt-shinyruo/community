package com.nowcoder.community.user.api;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.dto.AvatarUploadTokenResponse;
import com.nowcoder.community.user.api.dto.UpdateAvatarRequest;
import com.nowcoder.community.user.api.dto.UserProfileResponse;
import com.nowcoder.community.user.api.dto.UserResolveResponse;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.service.AvatarService;
import com.nowcoder.community.user.service.PointsService;
import com.nowcoder.community.user.service.SocialServiceClient;
import com.nowcoder.community.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.nowcoder.community.common.api.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AvatarService avatarService;
    private final SocialServiceClient socialServiceClient;
    private final PointsService pointsService;

    public UserController(UserService userService, AvatarService avatarService, SocialServiceClient socialServiceClient, PointsService pointsService) {
        this.userService = userService;
        this.avatarService = avatarService;
        this.socialServiceClient = socialServiceClient;
        this.pointsService = pointsService;
    }

    @GetMapping("/{userId}")
    public Result<UserProfileResponse> getUser(Authentication authentication, @PathVariable int userId) {
        User user = userService.getById(userId);
        UserProfileResponse resp = new UserProfileResponse();
        resp.setId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setHeaderUrl(user.getHeaderUrl());
        resp.setType(user.getType());
        resp.setStatus(user.getStatus());
        resp.setCreateTime(user.getCreateTime());
        resp.setScore(user.getScore());
        resp.setLevel(pointsService.levelForScore(user.getScore()));

        // 对齐旧单体“用户主页”展示：获赞/关注/粉丝 + 是否已关注（可选，未登录时为 false）
        resp.setLikeCount(socialServiceClient.safeUserLikeCount(userId));
        resp.setFolloweeCount(socialServiceClient.safeFolloweeCount(userId));
        resp.setFollowerCount(socialServiceClient.safeFollowerCount(userId));

        String tokenValue = null;
        int currentUserId = 0;
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            tokenValue = jwt.getTokenValue();
            try {
                currentUserId = Integer.parseInt(jwt.getSubject());
            } catch (Exception ignored) {
            }
        }
        if (currentUserId > 0 && currentUserId != userId && tokenValue != null) {
            resp.setHasFollowed(socialServiceClient.safeHasFollowed("Bearer " + tokenValue, userId));
        } else {
            resp.setHasFollowed(false);
        }
        return Result.ok(resp);
    }

    @GetMapping("/resolve")
    public Result<UserResolveResponse> resolveByUsername(@RequestParam String username) {
        User user = userService.getByUsername(username);
        UserResolveResponse resp = new UserResolveResponse();
        resp.setId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setHeaderUrl(user.getHeaderUrl());
        return Result.ok(resp);
    }

    @GetMapping("/{userId}/avatar/upload-token")
    public Result<AvatarUploadTokenResponse> uploadToken(Authentication authentication, @PathVariable int userId) {
        int currentUserId = currentUserId(authentication);
        if (currentUserId != userId) {
            throw new BusinessException(FORBIDDEN, "只能操作自己的头像");
        }
        return Result.ok(avatarService.createUploadToken());
    }

    @PutMapping("/{userId}/avatar")
    public Result<Void> updateAvatar(Authentication authentication, @PathVariable int userId, @Valid @RequestBody UpdateAvatarRequest request) {
        int currentUserId = currentUserId(authentication);
        if (currentUserId != userId) {
            throw new BusinessException(FORBIDDEN, "只能操作自己的头像");
        }
        String url = avatarService.buildAvatarUrl(request.getFileName());
        userService.updateHeaderUrl(userId, url);
        return Result.ok();
    }

    private int currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "未获取到认证信息");
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String sub = jwt.getSubject();
        try {
            return Integer.parseInt(sub);
        } catch (Exception e) {
            throw new BusinessException(INVALID_ARGUMENT, "token subject 非法");
        }
    }
}
