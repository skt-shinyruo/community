package com.nowcoder.community.content.service;

import com.nowcoder.community.content.app.moderation.TakeModerationActionUseCase;
import com.nowcoder.community.content.dto.ModerationActionRequest;
import com.nowcoder.community.content.dto.ModerationActionResponse;
import com.nowcoder.community.content.dto.ReportResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ModerationApplicationService {

    private final ModerationService moderationService;
    private final TakeModerationActionUseCase takeModerationActionUseCase;

    public ModerationApplicationService(
            ModerationService moderationService,
            TakeModerationActionUseCase takeModerationActionUseCase
    ) {
        this.moderationService = moderationService;
        this.takeModerationActionUseCase = takeModerationActionUseCase;
    }

    public List<ReportResponse> listReports(Integer status, Integer targetType, UUID reporterId, Integer page, Integer size) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 20 : Math.min(100, Math.max(1, size));
        return moderationService.listReportResponses(status, targetType, reporterId, p, s);
    }

    public UUID takeAction(UUID actorId, ModerationActionRequest request) {
        return takeModerationActionUseCase.takeAction(
                actorId,
                request.getReportId(),
                request.getAction(),
                request.getReason(),
                request.getDurationSeconds()
        );
    }

    public List<ModerationActionResponse> listActions(UUID actorId, Integer page, Integer size) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 20 : Math.min(100, Math.max(1, size));
        return moderationService.listModerationActionResponses(actorId, p, s);
    }
}
