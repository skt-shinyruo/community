package com.nowcoder.community.search.api.dto;

public class ReindexResponse {

    private String jobId;

    private int indexedCount;

    public ReindexResponse() {
    }

    public ReindexResponse(int indexedCount) {
        this.indexedCount = indexedCount;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public int getIndexedCount() {
        return indexedCount;
    }

    public void setIndexedCount(int indexedCount) {
        this.indexedCount = indexedCount;
    }
}
