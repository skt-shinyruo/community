package com.nowcoder.community.content.controller.dto;

import com.nowcoder.community.content.application.result.CommentPageResult;

import java.util.List;

public class CommentPageResponse {

    private List<CommentResponse> items;
    private String nextCursor;

    public static CommentPageResponse from(CommentPageResult page) {
        CommentPageResponse response = new CommentPageResponse();
        List<CommentResponse> mapped = page == null
                ? List.of()
                : page.items().stream().map(CommentResponse::from).toList();
        response.items = mapped;
        response.nextCursor = page == null ? "" : page.nextCursor();
        return response;
    }

    public List<CommentResponse> getItems() {
        return items;
    }

    public void setItems(List<CommentResponse> items) {
        this.items = items;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }
}
