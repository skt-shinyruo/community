package com.nowcoder.community.user.api;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.user.api.dto.AvatarUploadTokenResponse;
import com.nowcoder.community.user.api.dto.BatchUserSummaryRequest;
import com.nowcoder.community.user.api.dto.UpdateAvatarRequest;
import com.nowcoder.community.user.api.dto.UserProfileResponse;
import com.nowcoder.community.user.api.dto.UserResolveResponse;
import com.nowcoder.community.user.api.dto.UserSummaryResponse;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.service.AvatarService;
import com.nowcoder.community.user.service.PointsService;
import com.nowcoder.community.user.service.UserSocialProfileService;
import com.nowcoder.community.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static com.nowcoder.community.contracts.api.CommonErrorCode.FORBIDDEN;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AvatarService avatarService;
    private final UserSocialProfileService userSocialProfileService;
    private final PointsService pointsService;

    public UserController(UserService userService, AvatarService avatarService, UserSocialProfileService userSocialProfileService, PointsService pointsService) {
        this.userService = userService;
        this.avatarService = avatarService;
        this.userSocialProfileService = userSocialProfileService;
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

        Integer maybeCurrentUserId = CurrentUser.tryUserId(authentication);
        int currentUserId = maybeCurrentUserId == null ? 0 : maybeCurrentUserId;
        int viewerId = (currentUserId > 0 && currentUserId != userId) ? currentUserId : 0;

        // 对齐旧单体“用户主页”展示：获赞/关注/粉丝 + 是否已关注（可选，未登录时为 false）
        UserSocialProfileService.UserProfileStats stats = userSocialProfileService.safeUserProfileStats(userId, viewerId);
        resp.setLikeCount(stats.getLikeCount());
        resp.setFolloweeCount(stats.getFolloweeCount());
        resp.setFollowerCount(stats.getFollowerCount());
        resp.setSocialDegraded(stats.isDegraded());
        resp.setHasFollowed(viewerId > 0 ? stats.isHasFollowed() : false);
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

    @PostMapping("/batch-summary")
    public Result<List<UserSummaryResponse>> batchSummary(@Valid @RequestBody BatchUserSummaryRequest request) {
        List<Integer> raw = request == null ? List.of() : request.getUserIds();
        if (raw == null || raw.isEmpty()) {
            return Result.ok(List.of());
        }

        LinkedHashSet<Integer> dedup = new LinkedHashSet<>();
        for (Integer id : raw) {
            if (id == null || id <= 0) {
                continue;
            }
            dedup.add(id);
            if (dedup.size() >= 200) {
                break;
            }
        }
        if (dedup.isEmpty()) {
            return Result.ok(List.of());
        }

        List<Integer> ids = new ArrayList<>(dedup);
        List<User> users = userService.listUserSummariesByIds(ids);
        Map<Integer, UserSummaryResponse> map = new HashMap<>();
        for (User u : users) {
            if (u == null || u.getId() <= 0) {
                continue;
            }
            UserSummaryResponse s = new UserSummaryResponse();
            s.setId(u.getId());
            s.setUsername(u.getUsername());
            s.setHeaderUrl(u.getHeaderUrl());
            s.setType(u.getType());
            map.put(u.getId(), s);
        }

        List<UserSummaryResponse> out = new ArrayList<>();
        for (Integer id : ids) {
            UserSummaryResponse s = map.get(id);
            if (s != null) {
                out.add(s);
            }
        }
        return Result.ok(out);
    }

    @GetMapping("/{userId}/avatar/upload-token")
    public Result<AvatarUploadTokenResponse> uploadToken(Authentication authentication, @PathVariable int userId) {
        int currentUserId = CurrentUser.requireUserId(authentication);
        if (currentUserId != userId) {
            throw new BusinessException(FORBIDDEN, "只能操作自己的头像");
        }
        return Result.ok(avatarService.createUploadToken(userId));
    }

    @PostMapping(value = "/{userId}/avatar/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Void> uploadAvatar(Authentication authentication, @PathVariable int userId, @RequestParam("file") MultipartFile file, @RequestParam("fileName") String fileName) {
        int currentUserId = CurrentUser.requireUserId(authentication);
        if (currentUserId != userId) {
            throw new BusinessException(FORBIDDEN, "只能操作自己的头像");
        }
        avatarService.upload(userId, fileName, file);
        return Result.ok();
    }

    @PutMapping("/{userId}/avatar")
    public Result<Void> updateAvatar(Authentication authentication, @PathVariable int userId, @Valid @RequestBody UpdateAvatarRequest request) {
        int currentUserId = CurrentUser.requireUserId(authentication);
        if (currentUserId != userId) {
            throw new BusinessException(FORBIDDEN, "只能操作自己的头像");
        }
        avatarService.assertAndConsumeUploadTicket(userId, request.getFileName());
        String url = avatarService.buildAvatarUrl(request.getFileName());
        userService.updateHeaderUrl(userId, url);
        return Result.ok();
    }
}
