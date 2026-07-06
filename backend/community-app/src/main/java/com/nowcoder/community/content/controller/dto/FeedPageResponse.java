package com.nowcoder.community.content.controller.dto;

import com.nowcoder.community.content.application.result.FeedPageResult;

import java.util.List;

public class FeedPageResponse {

    private List<PostSummaryResponse> items;
    private String nextCursor;
    private String rankVersion;

    public static FeedPageResponse from(FeedPageResult page) {
        FeedPageResponse response = new FeedPageResponse();
        response.setItems(page == null || page.items() == null
                ? List.of()
                : page.items().stream().map(PostSummaryResponse::from).toList());
        response.setNextCursor(page == null || page.nextCursor() == null ? "" : page.nextCursor());
        response.setRankVersion(page == null || page.rankVersion() == null ? "" : page.rankVersion());
        return response;
    }

    public List<PostSummaryResponse> getItems() {
        return items;
    }

    public void setItems(List<PostSummaryResponse> items) {
        this.items = items;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public String getRankVersion() {
        return rankVersion;
    }

    public void setRankVersion(String rankVersion) {
        this.rankVersion = rankVersion;
    }
}
