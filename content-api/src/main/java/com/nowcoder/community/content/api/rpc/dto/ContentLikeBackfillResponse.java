package com.nowcoder.community.content.api.rpc.dto;

public class ContentLikeBackfillResponse {

    private int entityType;
    private long scannedItems;
    private long addedMembers;
    private int pages;

    public int getEntityType() {
        return entityType;
    }

    public void setEntityType(int entityType) {
        this.entityType = entityType;
    }

    public long getScannedItems() {
        return scannedItems;
    }

    public void setScannedItems(long scannedItems) {
        this.scannedItems = scannedItems;
    }

    public long getAddedMembers() {
        return addedMembers;
    }

    public void setAddedMembers(long addedMembers) {
        this.addedMembers = addedMembers;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }
}

