// 治理后台 API：版主/管理员查看举报队列并执行处置动作，含审计查询。
package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.dto.ModerationActionResponse;
import com.nowcoder.community.content.dto.ModerationActionRequest;
import com.nowcoder.community.content.dto.ReportResponse;
import com.nowcoder.community.content.service.ModerationApplicationService;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/moderation")
public class ModerationController {

    private final ModerationApplicationService moderationApplicationService;

    public ModerationController(ModerationApplicationService moderationApplicationService) {
        this.moderationApplicationService = moderationApplicationService;
    }

    @GetMapping("/reports")
    public Result<List<ReportResponse>> reports(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer targetType,
            @RequestParam(required = false) UUID reporterId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(moderationApplicationService.listReports(status, targetType, reporterId, page, size));
    }

    @PostMapping("/actions")
    public Result<UUID> action(Authentication authentication, @Valid @RequestBody ModerationActionRequest request) {
        UUID actorId = CurrentUser.requireUserUuid(authentication);
        UUID id = moderationApplicationService.takeAction(actorId, request);
        return Result.ok(id);
    }

    @GetMapping("/actions")
    public Result<List<ModerationActionResponse>> actions(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(moderationApplicationService.listActions(actorId, page, size));
    }
}
