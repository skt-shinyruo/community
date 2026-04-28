// 治理后台 API：版主/管理员查看举报队列并执行处置动作，含审计查询。
package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.application.ModerationApplicationService;
import com.nowcoder.community.content.application.command.TakeModerationActionCommand;
import com.nowcoder.community.content.application.result.ModerationActionResult;
import com.nowcoder.community.content.application.result.ReportModerationResult;
import com.nowcoder.community.content.controller.dto.ModerationActionResponse;
import com.nowcoder.community.content.controller.dto.ModerationActionRequest;
import com.nowcoder.community.content.controller.dto.ReportResponse;
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
        return Result.ok(moderationApplicationService.listReports(status, targetType, reporterId, page, size).stream()
                .map(this::toReportResponse)
                .toList());
    }

    @PostMapping("/actions")
    public Result<UUID> action(Authentication authentication, @Valid @RequestBody ModerationActionRequest request) {
        UUID actorId = CurrentUser.requireUserUuid(authentication);
        UUID id = moderationApplicationService.takeAction(new TakeModerationActionCommand(
                actorId,
                request.getReportId(),
                request.getAction(),
                request.getReason(),
                request.getDurationSeconds()
        ));
        return Result.ok(id);
    }

    @GetMapping("/actions")
    public Result<List<ModerationActionResponse>> actions(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(moderationApplicationService.listActions(actorId, page, size).stream()
                .map(this::toModerationActionResponse)
                .toList());
    }

    private ReportResponse toReportResponse(ReportModerationResult result) {
        ReportResponse response = new ReportResponse();
        response.setId(result.id());
        response.setReporterId(result.reporterId());
        response.setTargetType(result.targetType());
        response.setTargetId(result.targetId());
        response.setReason(result.reason());
        response.setDetail(result.detail());
        response.setStatus(result.status());
        response.setCreateTime(result.createTime());
        return response;
    }

    private ModerationActionResponse toModerationActionResponse(ModerationActionResult result) {
        ModerationActionResponse response = new ModerationActionResponse();
        response.setId(result.id());
        response.setReportId(result.reportId());
        response.setActorId(result.actorId());
        response.setAction(result.action());
        response.setReason(result.reason());
        response.setDurationSeconds(result.durationSeconds());
        response.setCreateTime(result.createTime());
        return response;
    }
}
