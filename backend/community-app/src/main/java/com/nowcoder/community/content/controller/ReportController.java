// 举报 API：登录用户提交举报（帖子/评论/用户）。
package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.dto.CreateReportRequest;
import com.nowcoder.community.content.dto.CreateReportResponse;
import com.nowcoder.community.content.service.ReportService;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public Result<CreateReportResponse> create(Authentication authentication, @Valid @RequestBody CreateReportRequest request) {
        int reporterId = CurrentUser.requireUserId(authentication);
        int targetType = parseTargetType(request.getTargetType());
        int targetId = request.getTargetId() == null ? 0 : request.getTargetId();
        int reportId = reportService.createReport(reporterId, targetType, targetId, request.getReason(), request.getDetail());

        CreateReportResponse resp = new CreateReportResponse();
        resp.setReportId(reportId);
        return Result.ok(resp);
    }

    private int parseTargetType(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase();
        if (s.isEmpty()) {
            throw new BusinessException(INVALID_ARGUMENT, "targetType 不能为空");
        }

        if ("post".equals(s) || "帖子".equals(s) || "1".equals(s)) {
            return ReportService.TARGET_TYPE_POST;
        }
        if ("comment".equals(s) || "评论".equals(s) || "2".equals(s)) {
            return ReportService.TARGET_TYPE_COMMENT;
        }
        if ("user".equals(s) || "用户".equals(s) || "3".equals(s)) {
            return ReportService.TARGET_TYPE_USER;
        }
        throw new BusinessException(INVALID_ARGUMENT, "targetType 非法");
    }
}
