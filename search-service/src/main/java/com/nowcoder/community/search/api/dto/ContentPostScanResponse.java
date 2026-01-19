package com.nowcoder.community.search.api.dto;

import com.nowcoder.community.common.event.payload.PostPayload;

import java.util.List;

/**
 * content-service 内部扫描帖子接口的响应模型（search-service 侧）。
 */
public class ContentPostScanResponse {

    private List<PostPayload> items;
    private int nextAfterId;
    private boolean hasMore;

    public List<PostPayload> getItems() {
        return items;
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

