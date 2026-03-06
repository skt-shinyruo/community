package com.nowcoder.community.auth.api.dto;

public class RegisterResponse {

    private int userId;

    /**
     * 注册后是否已触发邮件发送（未配置 SMTP 时会降级为日志输出，这里仍返回 true 表示“已生成激活链接”）。
     */
    private boolean activationIssued;

    /**
     * 仅用于本地/测试联调：回传激活链接，生产环境应关闭。
     */
    private String activationLink;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public boolean isActivationIssued() {
        return activationIssued;
    }

    public void setActivationIssued(boolean activationIssued) {
        this.activationIssued = activationIssued;
    }

    public String getActivationLink() {
        return activationLink;
    }

    public void setActivationLink(String activationLink) {
        this.activationLink = activationLink;
    }
}

