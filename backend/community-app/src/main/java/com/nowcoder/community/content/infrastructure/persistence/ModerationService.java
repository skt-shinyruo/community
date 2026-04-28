// 治理服务：处理举报审核与处置动作（隐藏/删除/警告/禁言/封禁），并记录审计与通知。
package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.infrastructure.persistence.mapper.ModerationActionMapper;
import com.nowcoder.community.content.domain.model.ModerationAction;
import com.nowcoder.community.content.domain.model.Report;
import com.nowcoder.community.infra.pagination.Pagination;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

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

    public List<Report> listReports(Integer status, Integer targetType, UUID reporterId, int page, int size) {
        return reportService.listReports(status, targetType, reporterId, page, size);
    }

    public List<ModerationAction> listActions(UUID actorId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        return actionMapper.selectActions(actorId, Pagination.safeOffset(p, s), s);
    }

}
