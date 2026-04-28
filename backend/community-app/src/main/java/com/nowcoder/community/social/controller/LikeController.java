package com.nowcoder.community.social.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.social.application.LikeApplicationService;
import com.nowcoder.community.social.application.command.SetLikeCommand;
import com.nowcoder.community.social.application.result.LikeResult;
import com.nowcoder.community.social.controller.dto.LikeRequest;
import com.nowcoder.community.social.controller.dto.LikeResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
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
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@RestController
@RequestMapping("/api/likes")
public class LikeController {

    private final LikeApplicationService likeApplicationService;

    public LikeController(LikeApplicationService likeApplicationService) {
        this.likeApplicationService = likeApplicationService;
    }

    @PostMapping
    public Result<LikeResponse> setLike(Authentication authentication, @Valid @RequestBody LikeRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        if (!EntityTypes.isValid(request.getEntityType())) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        LikeResult result = likeApplicationService.setLike(new SetLikeCommand(
                userId,
                request.getEntityType(),
                request.getEntityId(),
                request.getLiked()
        ));
        return Result.ok(toResponse(result));
    }

    @GetMapping("/status")
    public Result<Boolean> status(
            Authentication authentication,
            @RequestParam int entityType,
            @RequestParam UUID entityId
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        if (!EntityTypes.isValid(entityType)) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        return Result.ok(likeApplicationService.isLiked(userId, entityType, entityId));
    }

    @GetMapping("/count")
    public Result<Long> count(@RequestParam int entityType, @RequestParam UUID entityId) {
        if (!EntityTypes.isValid(entityType)) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        return Result.ok(likeApplicationService.count(entityType, entityId));
    }

    @GetMapping("/counts")
    public Result<Map<UUID, Long>> counts(@RequestParam int entityType, @RequestParam String entityIds) {
        if (!EntityTypes.isValid(entityType)) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        List<UUID> ids = parseEntityIds(entityIds, 200);
        return Result.ok(likeApplicationService.counts(entityType, ids));
    }

    @GetMapping("/statuses")
    public Result<Map<UUID, Boolean>> statuses(Authentication authentication, @RequestParam int entityType, @RequestParam String entityIds) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        if (!EntityTypes.isValid(entityType)) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        List<UUID> ids = parseEntityIds(entityIds, 200);
        return Result.ok(likeApplicationService.statuses(userId, entityType, ids));
    }

    @GetMapping("/users/{userId}/count")
    public Result<Long> userLikeCount(@PathVariable UUID userId) {
        return Result.ok(likeApplicationService.userLikeCount(userId));
    }

    private List<UUID> parseEntityIds(String raw, int limit) {
        if (raw == null) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<UUID> set = new LinkedHashSet<>();
        for (String part : trimmed.split(",")) {
            if (set.size() >= Math.max(1, limit)) {
                break;
            }
            String p = part == null ? "" : part.trim();
            if (p.isEmpty()) {
                continue;
            }
            try {
                set.add(UUID.fromString(p));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (set.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(set);
    }

    private LikeResponse toResponse(LikeResult result) {
        LikeResponse response = new LikeResponse();
        response.setLiked(result.liked());
        response.setLikeCount(result.likeCount());
        return response;
    }
}
