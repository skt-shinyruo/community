// 举报服务：负责举报写入、去重、基础校验与后台分页查询。
package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.repository.ReportContentRepository;
import com.nowcoder.community.content.infrastructure.persistence.mapper.ReportMapper;
import com.nowcoder.community.content.domain.model.Report;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class MyBatisReportContentRepository implements ReportContentRepository {

    private final ReportMapper reportMapper;
    private final UuidV7Generator idGenerator;

    public MyBatisReportContentRepository(ReportMapper reportMapper, UuidV7Generator idGenerator) {
        this.reportMapper = Objects.requireNonNull(reportMapper, "reportMapper must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
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
}
