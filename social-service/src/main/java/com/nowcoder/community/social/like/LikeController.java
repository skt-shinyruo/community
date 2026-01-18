package com.nowcoder.community.social.like;

import com.nowcoder.community.common.api.Result;
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

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

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
        return Result.ok(likeService.setLike(userId, request));
    }

    @GetMapping("/status")
    public Result<Boolean> status(
            Authentication authentication,
            @RequestParam int entityType,
            @RequestParam int entityId
    ) {
        int userId = currentUserId(authentication);
        return Result.ok(likeService.isLiked(userId, entityType, entityId));
    }

    @GetMapping("/count")
    public Result<Long> count(@RequestParam int entityType, @RequestParam int entityId) {
        return Result.ok(likeService.count(entityType, entityId));
    }

    @GetMapping("/users/{userId}/count")
    public Result<Long> userLikeCount(@PathVariable int userId) {
        return Result.ok(likeService.userLikeCount(userId));
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

