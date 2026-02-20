package com.nowcoder.community.social.projection;

import java.util.Date;

/**
 * content 实体元信息投影（供 social 写路径读取）：
 * - entityType/entityId：指向被点赞/关注的实体（POST/COMMENT）
 * - entityUserId/postId：用于构造可信事件 payload（禁止信任客户端注入字段）
 * - status：用于标记实体是否可用（删除/隐藏后应不可用）
 */
public class ContentEntityProjection {

    private int entityType;
    private long entityId;
    private long entityUserId;
    private long postId;
    private int status;
    private Date updatedAt;

    public int getEntityType() {
        return entityType;
    }

    public void setEntityType(int entityType) {
        this.entityType = entityType;
    }

    public long getEntityId() {
        return entityId;
    }

    public void setEntityId(long entityId) {
        this.entityId = entityId;
    }

    public long getEntityUserId() {
        return entityUserId;
    }

    public void setEntityUserId(long entityUserId) {
        this.entityUserId = entityUserId;
    }

    public long getPostId() {
        return postId;
    }

    public void setPostId(long postId) {
        this.postId = postId;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}

