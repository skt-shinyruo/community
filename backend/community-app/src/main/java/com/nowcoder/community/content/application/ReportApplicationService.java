package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.repository.ReportContentRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class ReportApplicationService {

    private final ReportContentRepository reportContentPort;

    public ReportApplicationService(ReportContentRepository reportContentPort) {
        this.reportContentPort = reportContentPort;
    }

    public UUID create(UUID reporterId, String rawTargetType, UUID targetId, String reason, String detail) {
        return reportContentPort.createReport(reporterId, parseTargetType(rawTargetType), targetId, reason, detail);
    }

    private int parseTargetType(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase();
        if (value.isEmpty()) {
            throw new BusinessException(INVALID_ARGUMENT, "targetType 不能为空");
        }
        if ("post".equals(value) || "帖子".equals(value) || "1".equals(value)) {
            return ReportContentRepository.TARGET_TYPE_POST;
        }
        if ("comment".equals(value) || "评论".equals(value) || "2".equals(value)) {
            return ReportContentRepository.TARGET_TYPE_COMMENT;
        }
        if ("user".equals(value) || "用户".equals(value) || "3".equals(value)) {
            return ReportContentRepository.TARGET_TYPE_USER;
        }
        throw new BusinessException(INVALID_ARGUMENT, "targetType 非法");
    }
}
