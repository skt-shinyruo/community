package com.nowcoder.community.search.api.dto;

public class ReindexResponse {

    private int indexedCount;

    public ReindexResponse() {
    }

    public ReindexResponse(int indexedCount) {
        this.indexedCount = indexedCount;
    }

    public int getIndexedCount() {
        return indexedCount;
    }

    public void setIndexedCount(int indexedCount) {
        this.indexedCount = indexedCount;
    }
}

