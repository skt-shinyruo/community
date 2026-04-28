// 拉黑 API：登录用户对其他用户拉黑/解除拉黑，并查询拉黑列表与状态。
package com.nowcoder.community.social.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.social.application.BlockApplicationService;
import com.nowcoder.community.social.application.command.BlockCommand;
import com.nowcoder.community.social.application.command.UnblockCommand;
import com.nowcoder.community.social.controller.dto.BlockRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/blocks")
public class BlockController {

    private final BlockApplicationService blockApplicationService;

    public BlockController(BlockApplicationService blockApplicationService) {
        this.blockApplicationService = blockApplicationService;
    }

    @PostMapping
    public Result<Void> block(Authentication authentication, @Valid @RequestBody BlockRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        blockApplicationService.block(new BlockCommand(userId, request.getUserId()));
        return Result.ok();
    }

    @DeleteMapping
    public Result<Void> unblock(Authentication authentication, @RequestParam UUID userId) {
        UUID actorId = CurrentUser.requireUserUuid(authentication);
        blockApplicationService.unblock(new UnblockCommand(actorId, userId));
        return Result.ok();
    }

    @GetMapping
    public Result<List<UUID>> list(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(blockApplicationService.listBlockedUserIds(userId));
    }

    @GetMapping("/status")
    public Result<Boolean> status(Authentication authentication, @RequestParam UUID userId) {
        UUID actorId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(blockApplicationService.hasBlocked(actorId, userId));
    }

}
