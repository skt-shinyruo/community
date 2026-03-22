package com.nowcoder.community.growth.dto;

public class RewardOrderResponse {

    private long id;
    private long itemId;
    private String status;
    private int costBalanceSnapshot;
    private String fulfillmentModeSnapshot;
    private String itemNameSnapshot;
    private String itemDescSnapshot;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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
}
