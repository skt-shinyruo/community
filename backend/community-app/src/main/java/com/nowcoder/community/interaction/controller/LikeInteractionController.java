package com.nowcoder.community.interaction.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.interaction.application.LikeInteractionApplicationService;
import com.nowcoder.community.interaction.application.LikeInteractionApplicationService.LikeResult;
import com.nowcoder.community.interaction.application.LikeInteractionApplicationService.SetLikeCommand;
import com.nowcoder.community.interaction.controller.dto.LikeRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/likes")
public class LikeInteractionController {

    private final LikeInteractionApplicationService applicationService;

    public LikeInteractionController(LikeInteractionApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public Result<LikeResult> setLike(Authentication authentication, @Valid @RequestBody LikeRequest request) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        LikeResult result = applicationService.setLike(new SetLikeCommand(
                actorUserId,
                request.getEntityType(),
                request.getEntityId(),
                request.getLiked()
        ));
        return Result.ok(result);
    }
}
