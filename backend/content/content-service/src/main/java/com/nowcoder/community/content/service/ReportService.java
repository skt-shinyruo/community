// 举报服务：负责举报写入、去重、基础校验与后台分页查询。
package com.nowcoder.community.content.service;

import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.contracts.domain.EntityTypes;
import com.nowcoder.community.content.dao.CommentMapper;
import com.nowcoder.community.content.dao.ReportMapper;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.Report;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

import static com.nowcoder.community.content.api.ContentErrorCode.COMMENT_NOT_FOUND;
import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.contracts.api.CommonErrorCode.NOT_FOUND;

@Service
public class ReportService {

    public static final int TARGET_TYPE_POST = EntityTypes.POST;
    public static final int TARGET_TYPE_COMMENT = EntityTypes.COMMENT;
    public static final int TARGET_TYPE_USER = EntityTypes.USER;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PROCESSED = 1;
    public static final int STATUS_REJECTED = 2;

    private static final int MAX_REASON_LEN = 64;
    private static final int MAX_DETAIL_LEN = 512;

    private final ReportMapper reportMapper;
    private final PostService postService;
    private final CommentMapper commentMapper;

    public ReportService(ReportMapper reportMapper, PostService postService, CommentMapper commentMapper) {
        this.reportMapper = reportMapper;
        this.postService = postService;
        this.commentMapper = commentMapper;
    }

    public int createReport(int reporterId, int targetType, int targetId, String reason, String detail) {
        if (reporterId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "reporterId 非法");
        }
        if (targetId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "targetId 非法");
        }
        if (targetType != TARGET_TYPE_POST && targetType != TARGET_TYPE_COMMENT && targetType != TARGET_TYPE_USER) {
            throw new BusinessException(INVALID_ARGUMENT, "targetType 非法");
        }

        String r = safeTrim(reason);
        if (!StringUtils.hasText(r)) {
            throw new BusinessException(INVALID_ARGUMENT, "reason 不能为空");
        }
        if (r.length() > MAX_REASON_LEN) {
            throw new BusinessException(INVALID_ARGUMENT, "reason 过长");
        }

        String d = safeTrim(detail);
        if (d.length() > MAX_DETAIL_LEN) {
            throw new BusinessException(INVALID_ARGUMENT, "detail 过长");
        }

        // 目标存在性校验：post/comment 在本服务内可校验；user 由后续处置阶段再兜底校验。
        if (targetType == TARGET_TYPE_POST) {
            postService.getById(targetId);
        }
        if (targetType == TARGET_TYPE_COMMENT) {
            Comment c = commentMapper.selectCommentById(targetId);
            if (c == null || c.getId() <= 0) {
                throw new BusinessException(COMMENT_NOT_FOUND);
            }
        }

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason(r);
        report.setDetail(d);
        report.setStatus(STATUS_PENDING);
        report.setCreateTime(new Date());

        try {
            reportMapper.insertReport(report);
            return report.getId();
        } catch (DuplicateKeyException ignored) {
            // 去重：同一用户对同一目标重复举报，返回已存在的 reportId（幂等）。
            Integer existed = reportMapper.selectReportIdByDedupeKey(reporterId, targetType, targetId);
            return existed == null ? 0 : existed;
        }
    }

    public Report getById(int reportId) {
        if (reportId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "reportId 非法");
        }
        Report report = reportMapper.selectReportById(reportId);
        if (report == null) {
            throw new BusinessException(NOT_FOUND, "举报不存在");
        }
        return report;
    }

    public List<Report> listReports(Integer status, Integer targetType, Integer reporterId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        Integer st = status == null ? null : status;
        Integer tt = targetType == null ? null : targetType;
        Integer rid = reporterId == null ? null : reporterId;
        if (rid != null && rid <= 0) {
            rid = null;
        }
        return reportMapper.selectReports(st, tt, rid, p * s, s);
    }

    public void markStatus(int reportId, int status) {
        int updated = reportMapper.updateStatus(reportId, status);
        if (updated <= 0) {
            throw new BusinessException(NOT_FOUND, "举报不存在或更新失败");
        }
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
