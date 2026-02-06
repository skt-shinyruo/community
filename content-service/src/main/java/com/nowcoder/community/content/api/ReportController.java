// 举报 API：登录用户提交举报（帖子/评论/用户）。
package com.nowcoder.community.content.api;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.api.dto.CreateReportRequest;
import com.nowcoder.community.content.api.dto.CreateReportResponse;
import com.nowcoder.community.content.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.CommonErrorCode.UNAUTHORIZED;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public Result<CreateReportResponse> create(Authentication authentication, @Valid @RequestBody CreateReportRequest request) {
        int reporterId = currentUserId(authentication);
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

    private int currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(UNAUTHORIZED, "未获取到认证信息");
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String sub = jwt.getSubject();
        try {
            return Integer.parseInt(sub);
        } catch (NumberFormatException e) {
            throw new BusinessException(INVALID_ARGUMENT, "token subject 非法");
        }
    }
}
