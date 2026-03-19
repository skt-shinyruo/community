// 创建举报响应：返回 reportId（用于后续查询/审核）。
package com.nowcoder.community.content.dto;

public class CreateReportResponse {

    private int reportId;

    public int getReportId() {
        return reportId;
    }

    public void setReportId(int reportId) {
        this.reportId = reportId;
    }
}

