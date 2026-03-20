package com.nowcoder.community.content.dto;

import com.nowcoder.community.content.event.payload.PostPayload;

import java.util.List;

public class PostScanResult {

    private List<PostPayload> items;
    private int nextAfterId;
    private boolean hasMore;

    public List<PostPayload> getItems() {
        return items == null ? List.of() : items;
    }

    public void setItems(List<PostPayload> items) {
        this.items = items;
    }

    public int getNextAfterId() {
        return nextAfterId;
    }

    public void setNextAfterId(int nextAfterId) {
        this.nextAfterId = nextAfterId;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}
