package com.nowcoder.community.content.contracts.event;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

/**
 * 处罚命令事件载荷：
 * - content 模块产生（举报处置触发 mute/ban）
 * - user 模块消费并执行（写入 user.mute_until/ban_until）
 *
 * <p>注意：这是“命令”，不是“通知”。当前在同进程内通过事务后本地事件投递。</p>
 */
public class ModerationCommandPayload {

    private UUID userId;
    private String action;
    private Integer durationSeconds;

    private UUID actorUserId;
    private UUID reportId;
    private String reason;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    @JsonIgnore
    public void setUserId(Integer userId) {
        throw new IllegalArgumentException("numeric userId 已不再受支持");
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(UUID actorUserId) {
        this.actorUserId = actorUserId;
    }

    @JsonIgnore
    public void setActorUserId(Integer actorUserId) {
        throw new IllegalArgumentException("numeric actorUserId 已不再受支持");
    }

    public UUID getReportId() {
        return reportId;
    }

    public void setReportId(UUID reportId) {
        this.reportId = reportId;
    }

    @JsonIgnore
    public void setReportId(Integer reportId) {
        throw new IllegalArgumentException("numeric reportId 已不再受支持");
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
