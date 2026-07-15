package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.model.ReportSnapshot;
import com.nowcoder.community.content.domain.repository.ReportRepository;
import com.nowcoder.community.content.domain.model.Report;
import com.nowcoder.community.content.infrastructure.persistence.mapper.ReportMapper;
import com.nowcoder.community.common.pagination.Pagination;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Repository
public class MyBatisReportRepository implements ReportRepository {

    private final ReportMapper reportMapper;

    public MyBatisReportRepository(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    @Override
    public ReportSnapshot getRequired(UUID reportId) {
        if (reportId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "reportId 非法");
        }
        Report report = reportMapper.selectReportById(reportId);
        if (report == null) {
            throw new BusinessException(NOT_FOUND, "举报不存在");
        }
        return toSnapshot(report);
    }

    @Override
    public List<ReportSnapshot> listReports(Integer status, Integer targetType, UUID reporterId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        return reportMapper.selectReports(status, targetType, reporterId, Pagination.safeOffset(p, s), s).stream()
                .map(this::toSnapshot)
                .toList();
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

    private ReportSnapshot toSnapshot(Report report) {
        return new ReportSnapshot(
                report.getId(),
                report.getReporterId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getDetail(),
                report.getStatus(),
                report.getCreateTime()
        );
    }
}
