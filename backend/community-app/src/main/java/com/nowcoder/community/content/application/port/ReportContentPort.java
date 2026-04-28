package com.nowcoder.community.content.application.port;

import com.nowcoder.community.content.domain.model.Report;

import java.util.List;
import java.util.UUID;

public interface ReportContentPort {

    int TARGET_TYPE_POST = 1;
    int TARGET_TYPE_COMMENT = 2;
    int TARGET_TYPE_USER = 3;

    int STATUS_PENDING = 0;
    int STATUS_PROCESSED = 1;
    int STATUS_REJECTED = 2;

    UUID createReport(UUID reporterId, int targetType, UUID targetId, String reason, String detail);

    Report getById(UUID reportId);

    List<Report> listReports(Integer status, Integer targetType, UUID reporterId, int page, int size);

    void markStatus(UUID reportId, int status);
}
