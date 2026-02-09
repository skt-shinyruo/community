package com.nowcoder.community.social.api.rpc.dto;

import java.io.Serializable;
import java.util.List;

public class SocialLikeScanResponse implements Serializable {

    private List<SocialLikeScanItem> items;
    private boolean hasMore;
    private Long nextAfterEntityId;
    private Long nextAfterUserId;

    public List<SocialLikeScanItem> getItems() {
        return items == null ? List.of() : items;
    }

    public void setItems(List<SocialLikeScanItem> items) {
        this.items = items;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

    public Long getNextAfterEntityId() {
        return nextAfterEntityId;
    }

    public void setNextAfterEntityId(Long nextAfterEntityId) {
        this.nextAfterEntityId = nextAfterEntityId;
    }

    public Long getNextAfterUserId() {
        return nextAfterUserId;
    }

    public void setNextAfterUserId(Long nextAfterUserId) {
        this.nextAfterUserId = nextAfterUserId;
    }

    public static class SocialLikeScanItem implements Serializable {
        private long entityId;
        private long userId;

        public long getEntityId() {
            return entityId;
        }

        public void setEntityId(long entityId) {
            this.entityId = entityId;
        }

        public long getUserId() {
            return userId;
        }

        public void setUserId(long userId) {
            this.userId = userId;
        }
    }
}

