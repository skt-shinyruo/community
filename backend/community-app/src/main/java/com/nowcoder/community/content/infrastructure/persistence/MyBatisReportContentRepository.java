// 举报服务：负责举报写入、去重、基础校验与后台分页查询。
package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.repository.ReportContentRepository;
import com.nowcoder.community.content.infrastructure.persistence.mapper.ReportMapper;
import com.nowcoder.community.content.domain.model.Report;
import com.nowcoder.community.common.pagination.Pagination;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class MyBatisReportContentRepository implements ReportContentRepository {

    private final ReportMapper reportMapper;
    private final UuidV7Generator idGenerator;

    @Autowired
    public MyBatisReportContentRepository(ReportMapper reportMapper) {
        this(reportMapper, new UuidV7Generator());
    }

    MyBatisReportContentRepository(ReportMapper reportMapper, UuidV7Generator idGenerator) {
        this.reportMapper = reportMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public UUID createReport(Report report) {
        if (report == null || report.getReporterId() == null || report.getTargetId() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "report 非法");
        }
        report.setId(report.getId() == null ? idGenerator.next() : report.getId());

        try {
            reportMapper.insertReport(report);
            return report.getId();
        } catch (DuplicateKeyException ignored) {
            UUID existed = reportMapper.selectReportIdByDedupeKey(report.getReporterId(), report.getTargetType(), report.getTargetId());
            if (existed != null) {
                return existed;
            }
            throw new BusinessException(INTERNAL_ERROR, "举报写入失败");
        }
    }

    @Override
    public UUID findExistingReportId(UUID reporterId, int targetType, UUID targetId) {
        if (reporterId == null || targetId == null) {
            return null;
        }
        return reportMapper.selectReportIdByDedupeKey(reporterId, targetType, targetId);
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
}
