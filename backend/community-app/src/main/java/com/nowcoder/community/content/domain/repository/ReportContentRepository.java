package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.Report;

import java.util.UUID;

public interface ReportContentRepository {

    int TARGET_TYPE_POST = 1;
    int TARGET_TYPE_COMMENT = 2;
    int TARGET_TYPE_USER = 3;

    int STATUS_PENDING = 0;
    int STATUS_PROCESSED = 1;
    int STATUS_REJECTED = 2;

    UUID createReport(Report report);

    UUID findExistingReportId(UUID reporterId, int targetType, UUID targetId);
}
