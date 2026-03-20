package com.nowcoder.community.content.event.payload;

/**
 * 处罚命令事件载荷：
 * - content 模块产生（举报处置触发 mute/ban）
 * - user 模块消费并执行（写入 user.mute_until/ban_until）
 *
 * <p>注意：这是“命令”，不是“通知”。当前在同进程内通过事务后本地事件投递。</p>
 */
public class ModerationCommandPayload {

    private Integer userId;
    private String action;
    private Integer durationSeconds;

    private Integer actorUserId;
    private Integer reportId;
    private String reason;

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
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

    public Integer getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Integer actorUserId) {
        this.actorUserId = actorUserId;
    }

    public Integer getReportId() {
        return reportId;
    }

    public void setReportId(Integer reportId) {
        this.reportId = reportId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
