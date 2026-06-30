package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.model.Report;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.ReportContentRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class ReportApplicationService {

    private static final int MAX_REASON_LEN = 64;
    private static final int MAX_DETAIL_LEN = 512;

    private final ReportContentRepository reportContentPort;
    private final PostContentRepository postContentRepository;
    private final CommentRepository commentRepository;

    public ReportApplicationService(ReportContentRepository reportContentPort,
                                    PostContentRepository postContentRepository,
                                    CommentRepository commentRepository) {
        this.reportContentPort = reportContentPort;
        this.postContentRepository = postContentRepository;
        this.commentRepository = commentRepository;
    }

    @Transactional
    public UUID create(UUID reporterId, String rawTargetType, UUID targetId, String reason, String detail) {
        if (reporterId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "reporterId 非法");
        }
        if (targetId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "targetId 非法");
        }
        int targetType = parseTargetType(rawTargetType);
        String normalizedReason = normalizeReason(reason);
        String normalizedDetail = normalizeDetail(detail);
        assertTargetExists(targetType, targetId);
        if (targetType == ReportContentRepository.TARGET_TYPE_USER && reporterId.equals(targetId)) {
            throw new BusinessException(FORBIDDEN, "不能举报自己");
        }
        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason(normalizedReason);
        report.setDetail(normalizedDetail);
        report.setStatus(ReportContentRepository.STATUS_PENDING);
        report.setCreateTime(new Date());
        try {
            return reportContentPort.createReport(report);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            UUID existed = reportContentPort.findExistingReportId(reporterId, targetType, targetId);
            if (existed != null) {
                return existed;
            }
            throw new BusinessException(INTERNAL_ERROR, "举报写入失败", ex);
        }
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

    private void assertTargetExists(int targetType, UUID targetId) {
        if (targetType == ReportContentRepository.TARGET_TYPE_POST) {
            postContentRepository.getById(targetId);
            return;
        }
        if (targetType == ReportContentRepository.TARGET_TYPE_COMMENT) {
            if (commentRepository.findActiveSnapshot(targetId).isEmpty()) {
                throw new BusinessException(NOT_FOUND, "资源不存在");
            }
        }
    }

    private String normalizeReason(String reason) {
        String value = safeTrim(reason);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "reason 不能为空");
        }
        if (value.length() > MAX_REASON_LEN) {
            throw new BusinessException(INVALID_ARGUMENT, "reason 过长");
        }
        return value;
    }

    private String normalizeDetail(String detail) {
        String value = safeTrim(detail);
        if (value.length() > MAX_DETAIL_LEN) {
            throw new BusinessException(INVALID_ARGUMENT, "detail 过长");
        }
        return value;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
