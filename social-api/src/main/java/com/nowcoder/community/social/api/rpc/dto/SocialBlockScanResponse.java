package com.nowcoder.community.social.api.rpc.dto;

import java.io.Serializable;
import java.util.List;

public class SocialBlockScanResponse implements Serializable {

    private List<SocialBlockScanItem> items;
    private boolean hasMore;
    private Integer nextAfterBlockerUserId;
    private Integer nextAfterBlockedUserId;

    public List<SocialBlockScanItem> getItems() {
        return items == null ? List.of() : items;
    }

    public void setItems(List<SocialBlockScanItem> items) {
        this.items = items;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

    public Integer getNextAfterBlockerUserId() {
        return nextAfterBlockerUserId;
    }

    public void setNextAfterBlockerUserId(Integer nextAfterBlockerUserId) {
        this.nextAfterBlockerUserId = nextAfterBlockerUserId;
    }

    public Integer getNextAfterBlockedUserId() {
        return nextAfterBlockedUserId;
    }

    public void setNextAfterBlockedUserId(Integer nextAfterBlockedUserId) {
        this.nextAfterBlockedUserId = nextAfterBlockedUserId;
    }

    public static class SocialBlockScanItem implements Serializable {
        private int blockerUserId;
        private int blockedUserId;

        public int getBlockerUserId() {
            return blockerUserId;
        }

        public void setBlockerUserId(int blockerUserId) {
            this.blockerUserId = blockerUserId;
        }

        public int getBlockedUserId() {
            return blockedUserId;
        }

        public void setBlockedUserId(int blockedUserId) {
            this.blockedUserId = blockedUserId;
        }
    }
}

