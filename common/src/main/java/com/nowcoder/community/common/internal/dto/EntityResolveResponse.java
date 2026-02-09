package com.nowcoder.community.common.internal.dto;

import java.io.Serializable;

/**
 * internal entity resolve 响应：用于跨服务写路径构造可信事件 payload（禁止信任客户端注入）。
 *
 * <p>约定：由内容域提供 POST/COMMENT 的 owner/postId 解析。</p>
 */
public class EntityResolveResponse implements Serializable {

    private int entityType;
    private int entityId;
    private int entityUserId;
    private int postId;

    public int getEntityType() {
        return entityType;
    }

    public void setEntityType(int entityType) {
        this.entityType = entityType;
    }

    public int getEntityId() {
        return entityId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }

    public int getEntityUserId() {
        return entityUserId;
    }

    public void setEntityUserId(int entityUserId) {
        this.entityUserId = entityUserId;
    }

    public int getPostId() {
        return postId;
    }

    public void setPostId(int postId) {
        this.postId = postId;
    }
}
