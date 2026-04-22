// 创建举报响应：返回 reportId（用于后续查询/审核）。
package com.nowcoder.community.content.dto;

import java.util.UUID;

public class CreateReportResponse {

    private UUID reportId;

    public UUID getReportId() {
        return reportId;
    }

    public void setReportId(UUID reportId) {
        this.reportId = reportId;
    }
}
