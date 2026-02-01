package com.nowcoder.community.social.like;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.domain.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.like.dto.LikeRequest;
import com.nowcoder.community.social.like.dto.LikeResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.CommonErrorCode.UNAUTHORIZED;

@RestController
@RequestMapping("/api/likes")
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @PostMapping
    public Result<LikeResponse> setLike(Authentication authentication, @Valid @RequestBody LikeRequest request) {
        int userId = currentUserId(authentication);
        if (!EntityTypes.isValid(request.getEntityType())) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        return Result.ok(likeService.setLike(userId, request));
    }

    @GetMapping("/status")
    public Result<Boolean> status(
            Authentication authentication,
            @RequestParam int entityType,
            @RequestParam int entityId
    ) {
        int userId = currentUserId(authentication);
        if (!EntityTypes.isValid(entityType)) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        return Result.ok(likeService.isLiked(userId, entityType, entityId));
    }

    @GetMapping("/count")
    public Result<Long> count(@RequestParam int entityType, @RequestParam int entityId) {
        if (!EntityTypes.isValid(entityType)) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        return Result.ok(likeService.count(entityType, entityId));
    }

    @GetMapping("/counts")
    public Result<Map<Integer, Long>> counts(@RequestParam int entityType, @RequestParam String entityIds) {
        if (!EntityTypes.isValid(entityType)) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        List<Integer> ids = parseEntityIds(entityIds, 200);
        return Result.ok(likeService.counts(entityType, ids));
    }

    @GetMapping("/statuses")
    public Result<Map<Integer, Boolean>> statuses(Authentication authentication, @RequestParam int entityType, @RequestParam String entityIds) {
        int userId = currentUserId(authentication);
        if (!EntityTypes.isValid(entityType)) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        List<Integer> ids = parseEntityIds(entityIds, 200);
        return Result.ok(likeService.statuses(userId, entityType, ids));
    }

    @GetMapping("/users/{userId}/count")
    public Result<Long> userLikeCount(@PathVariable int userId) {
        return Result.ok(likeService.userLikeCount(userId));
    }

    private int currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(UNAUTHORIZED, "未获取到认证信息");
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String sub = jwt.getSubject();
        try {
            return Integer.parseInt(sub);
        } catch (Exception e) {
            throw new BusinessException(INVALID_ARGUMENT, "token subject 非法");
        }
    }

    private List<Integer> parseEntityIds(String raw, int limit) {
        if (raw == null) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        for (String part : trimmed.split(",")) {
            if (set.size() >= Math.max(1, limit)) {
                break;
            }
            String p = part == null ? "" : part.trim();
            if (p.isEmpty()) {
                continue;
            }
            try {
                int id = Integer.parseInt(p);
                if (id > 0) {
                    set.add(id);
                }
            } catch (Exception ignored) {
            }
        }
        if (set.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(set);
    }
}
