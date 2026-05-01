// 举报服务：负责举报写入、去重、基础校验与后台分页查询。
package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.ReportContentRepository;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import com.nowcoder.community.content.infrastructure.persistence.mapper.ReportMapper;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.Report;
import com.nowcoder.community.infra.pagination.Pagination;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;
import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class MyBatisReportContentRepository implements ReportContentRepository {

    public static final int TARGET_TYPE_POST = EntityTypes.POST;
    public static final int TARGET_TYPE_COMMENT = EntityTypes.COMMENT;
    public static final int TARGET_TYPE_USER = EntityTypes.USER;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PROCESSED = 1;
    public static final int STATUS_REJECTED = 2;

    private static final int MAX_REASON_LEN = 64;
    private static final int MAX_DETAIL_LEN = 512;

    private final ReportMapper reportMapper;
    private final PostContentRepository postContentPort;
    private final CommentMapper commentMapper;
    private final UuidV7Generator idGenerator;

    @Autowired
    public MyBatisReportContentRepository(ReportMapper reportMapper, PostContentRepository postContentPort, CommentMapper commentMapper) {
        this(reportMapper, postContentPort, commentMapper, new UuidV7Generator());
    }

    MyBatisReportContentRepository(ReportMapper reportMapper, PostContentRepository postContentPort, CommentMapper commentMapper, UuidV7Generator idGenerator) {
        this.reportMapper = reportMapper;
        this.postContentPort = postContentPort;
        this.commentMapper = commentMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public UUID createReport(UUID reporterId, int targetType, UUID targetId, String reason, String detail) {
        if (reporterId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "reporterId 非法");
        }
        if (targetId == null) {
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
            postContentPort.getById(targetId);
        }
        if (targetType == TARGET_TYPE_COMMENT) {
            Comment c = commentMapper.selectCommentById(targetId);
            if (c == null || c.getStatus() != 0) {
                throw new BusinessException(COMMENT_NOT_FOUND);
            }
        }

        Report report = new Report();
        report.setId(idGenerator.next());
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
            UUID existed = reportMapper.selectReportIdByDedupeKey(reporterId, targetType, targetId);
            if (existed != null) {
                return existed;
            }
            throw new BusinessException(INTERNAL_ERROR, "举报写入失败");
        }
    }

    @Override
    public Report getById(UUID reportId) {
        if (reportId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "reportId 非法");
        }
        Report report = reportMapper.selectReportById(reportId);
        if (report == null) {
            throw new BusinessException(NOT_FOUND, "举报不存在");
        }
        return report;
    }

    @Override
    public List<Report> listReports(Integer status, Integer targetType, UUID reporterId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        Integer st = status == null ? null : status;
        Integer tt = targetType == null ? null : targetType;
        return reportMapper.selectReports(st, tt, reporterId, Pagination.safeOffset(p, s), s);
    }

    @Override
    public void markStatus(UUID reportId, int status) {
        if (reportId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "reportId 非法");
        }
        int updated = reportMapper.updateStatus(reportId, status);
        if (updated <= 0) {
            throw new BusinessException(NOT_FOUND, "举报不存在或更新失败");
        }
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
