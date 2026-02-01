package com.nowcoder.community.content.like;

import java.util.List;

/**
 * social-service internal likes 扫描响应（用于回填 Redis 点赞投影）。
 *
 * <p>注意：该 DTO 仅用于 internal client 反序列化，字段需与 social-service 输出保持一致。</p>
 */
public class SocialLikeScanResponse {

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

    public static class SocialLikeScanItem {
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

