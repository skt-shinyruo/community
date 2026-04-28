// 举报 API：登录用户提交举报（帖子/评论/用户）。
package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.controller.dto.CreateReportRequest;
import com.nowcoder.community.content.controller.dto.CreateReportResponse;
import com.nowcoder.community.content.application.ReportApplicationService;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportApplicationService reportApplicationService;

    public ReportController(ReportApplicationService reportApplicationService) {
        this.reportApplicationService = reportApplicationService;
    }

    @PostMapping
    public Result<CreateReportResponse> create(Authentication authentication, @Valid @RequestBody CreateReportRequest request) {
        UUID reporterId = CurrentUser.requireUserUuid(authentication);
        UUID reportId = reportApplicationService.create(
                reporterId,
                request.getTargetType(),
                request.getTargetId(),
                request.getReason(),
                request.getDetail()
        );

        CreateReportResponse resp = new CreateReportResponse();
        resp.setReportId(reportId);
        return Result.ok(resp);
    }
}
