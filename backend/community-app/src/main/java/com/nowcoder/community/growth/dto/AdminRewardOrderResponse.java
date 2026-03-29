package com.nowcoder.community.growth.dto;

import java.util.Date;

public class AdminRewardOrderResponse {

    private long id;
    private String redeemRequestId;
    private int userId;
    private long itemId;
    private String status;
    private int costBalanceSnapshot;
    private String fulfillmentModeSnapshot;
    private String itemNameSnapshot;
    private String itemDescSnapshot;
    private Date createTime;
    private Date updateTime;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getRedeemRequestId() {
        return redeemRequestId;
    }

    public void setRedeemRequestId(String redeemRequestId) {
        this.redeemRequestId = redeemRequestId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public long getItemId() {
        return itemId;
    }

    public void setItemId(long itemId) {
        this.itemId = itemId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCostBalanceSnapshot() {
        return costBalanceSnapshot;
    }

    public void setCostBalanceSnapshot(int costBalanceSnapshot) {
        this.costBalanceSnapshot = costBalanceSnapshot;
    }

    public String getFulfillmentModeSnapshot() {
        return fulfillmentModeSnapshot;
    }

    public void setFulfillmentModeSnapshot(String fulfillmentModeSnapshot) {
        this.fulfillmentModeSnapshot = fulfillmentModeSnapshot;
    }

    public String getItemNameSnapshot() {
        return itemNameSnapshot;
    }

    public void setItemNameSnapshot(String itemNameSnapshot) {
        this.itemNameSnapshot = itemNameSnapshot;
    }

    public String getItemDescSnapshot() {
        return itemDescSnapshot;
    }

    public void setItemDescSnapshot(String itemDescSnapshot) {
        this.itemDescSnapshot = itemDescSnapshot;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
