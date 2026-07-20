package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.ReportSnapshot;

import java.util.List;
import java.util.UUID;

public interface ReportRepository {

    ReportSnapshot getRequired(UUID reportId);

    List<ReportSnapshot> listReports(Integer status, Integer targetType, UUID reporterId, int page, int size);

    boolean claimPending(UUID reportId);

    boolean transitionStatus(UUID reportId, int expectedStatus, int nextStatus);

    void markStatus(UUID reportId, int status);
}
