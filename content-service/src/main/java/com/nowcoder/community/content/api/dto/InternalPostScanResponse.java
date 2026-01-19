package com.nowcoder.community.content.api.dto;

import com.nowcoder.community.common.event.payload.PostPayload;

import java.util.List;

/**
 * content-service 内部接口：按游标扫描帖子，用于 search-service 重建索引等后台任务。
 */
public class InternalPostScanResponse {

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

