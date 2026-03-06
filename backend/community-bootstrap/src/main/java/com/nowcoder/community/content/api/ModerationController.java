// 治理后台 API：版主/管理员查看举报队列并执行处置动作，含审计查询。
package com.nowcoder.community.content.api;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.content.api.dto.ModerationActionRequest;
import com.nowcoder.community.content.entity.ModerationAction;
import com.nowcoder.community.content.entity.Report;
import com.nowcoder.community.content.service.ModerationService;
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

@RestController
@RequestMapping("/api/moderation")
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @GetMapping("/reports")
    public Result<List<Report>> reports(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer targetType,
            @RequestParam(required = false) Integer reporterId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 20 : Math.min(100, Math.max(1, size));
        return Result.ok(moderationService.listReports(status, targetType, reporterId, p, s));
    }

    @PostMapping("/actions")
    public Result<Integer> action(Authentication authentication, @Valid @RequestBody ModerationActionRequest request) {
        int actorId = CurrentUser.requireUserId(authentication);
        int id = moderationService.takeAction(
                actorId,
                request.getReportId(),
                request.getAction(),
                request.getReason(),
                request.getDurationSeconds()
        );
        return Result.ok(id);
    }

    @GetMapping("/actions")
    public Result<List<ModerationAction>> actions(
            @RequestParam(required = false) Integer actorId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 20 : Math.min(100, Math.max(1, size));
        return Result.ok(moderationService.listActions(actorId, p, s));
    }
}
