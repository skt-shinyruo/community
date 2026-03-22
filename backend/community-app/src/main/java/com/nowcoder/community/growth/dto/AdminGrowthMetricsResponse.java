package com.nowcoder.community.growth.dto;

public class AdminGrowthMetricsResponse {

    private int activeItemCount;
    private int pendingOrderCount;
    private int refundedOrderCount;

    public int getActiveItemCount() {
        return activeItemCount;
    }

    public void setActiveItemCount(int activeItemCount) {
        this.activeItemCount = activeItemCount;
    }

    public int getPendingOrderCount() {
        return pendingOrderCount;
    }

    public void setPendingOrderCount(int pendingOrderCount) {
        this.pendingOrderCount = pendingOrderCount;
    }

    public int getRefundedOrderCount() {
        return refundedOrderCount;
    }

    public void setRefundedOrderCount(int refundedOrderCount) {
        this.refundedOrderCount = refundedOrderCount;
    }
}
