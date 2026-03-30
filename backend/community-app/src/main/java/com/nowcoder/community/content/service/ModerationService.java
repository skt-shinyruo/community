// 治理服务：处理举报审核与处置动作（隐藏/删除/警告/禁言/封禁），并记录审计与通知。
package com.nowcoder.community.content.service;

import com.nowcoder.community.content.dto.ModerationActionResponse;
import com.nowcoder.community.content.dto.ReportResponse;
import com.nowcoder.community.content.mapper.ModerationActionMapper;
import com.nowcoder.community.content.entity.ModerationAction;
import com.nowcoder.community.content.entity.Report;
import com.nowcoder.community.infra.pagination.Pagination;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ModerationService {

    public static final String ACTION_REJECT = "reject";
    public static final String ACTION_HIDE = "hide";
    public static final String ACTION_DELETE = "delete";
    public static final String ACTION_WARN = "warn";
    public static final String ACTION_MUTE = "mute";
    public static final String ACTION_BAN = "ban";

    private final ReportService reportService;
    private final ModerationActionMapper actionMapper;

    public ModerationService(
            ReportService reportService,
            ModerationActionMapper actionMapper
    ) {
        this.reportService = reportService;
        this.actionMapper = actionMapper;
    }

    public List<Report> listReports(Integer status, Integer targetType, Integer reporterId, int page, int size) {
        return reportService.listReports(status, targetType, reporterId, page, size);
    }

    public List<ReportResponse> listReportResponses(Integer status, Integer targetType, Integer reporterId, int page, int size) {
        return listReports(status, targetType, reporterId, page, size).stream()
                .map(this::toReportResponse)
                .toList();
    }

    public List<ModerationAction> listActions(Integer actorId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        Integer aid = actorId == null ? null : actorId;
        if (aid != null && aid <= 0) {
            aid = null;
        }
        return actionMapper.selectActions(aid, Pagination.safeOffset(p, s), s);
    }

    public List<ModerationActionResponse> listModerationActionResponses(Integer actorId, int page, int size) {
        return listActions(actorId, page, size).stream()
                .map(this::toModerationActionResponse)
                .toList();
    }

    private ReportResponse toReportResponse(Report report) {
        ReportResponse response = new ReportResponse();
        response.setId(report.getId());
        response.setReporterId(report.getReporterId());
        response.setTargetType(report.getTargetType());
        response.setTargetId(report.getTargetId());
        response.setReason(report.getReason());
        response.setDetail(report.getDetail());
        response.setStatus(report.getStatus());
        response.setCreateTime(report.getCreateTime());
        return response;
    }

    private ModerationActionResponse toModerationActionResponse(ModerationAction action) {
        ModerationActionResponse response = new ModerationActionResponse();
        response.setId(action.getId());
        response.setReportId(action.getReportId());
        response.setActorId(action.getActorId());
        response.setAction(action.getAction());
        response.setReason(action.getReason());
        response.setDurationSeconds(action.getDurationSeconds());
        response.setCreateTime(action.getCreateTime());
        return response;
    }
}
