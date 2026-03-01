package com.nowcoder.community.content.api.dto;

import com.nowcoder.community.content.entity.Comment;

import java.util.Date;
import java.util.function.Function;

/**
 * 评论/回复公共响应 DTO（字段白名单）。
 *
 * <p>说明：避免直接返回 entity 导致治理字段（status/deleted* 等）对外泄露。</p>
 */
public class CommentResponse {

    private int id;
    private int userId;
    private int entityType;
    private int entityId;
    private int targetId;
    private String content;
    private Date createTime;
    private Date updateTime;
    private int editCount;

    public static CommentResponse from(Comment c) {
        return from(c, null);
    }

    public static CommentResponse from(Comment c, Function<String, String> contentDecoder) {
        if (c == null) {
            return null;
        }
        CommentResponse r = new CommentResponse();
        r.id = c.getId();
        r.userId = c.getUserId();
        r.entityType = c.getEntityType();
        r.entityId = c.getEntityId();
        r.targetId = c.getTargetId();
        String raw = c.getContent();
        r.content = contentDecoder == null ? raw : contentDecoder.apply(raw);
        r.createTime = c.getCreateTime();
        r.updateTime = c.getUpdateTime();
        r.editCount = c.getEditCount();
        return r;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

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

    public int getTargetId() {
        return targetId;
    }

    public void setTargetId(int targetId) {
        this.targetId = targetId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public int getEditCount() {
        return editCount;
    }

    public void setEditCount(int editCount) {
        this.editCount = editCount;
    }
}
