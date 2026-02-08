package com.nowcoder.community.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.registration")
public class RegistrationProperties {

    /**
     * 激活链接的 base URL（建议指向 gateway/前端可访问入口）。
     *
     * <p>说明：为了避免在非本地环境“静默回退到 localhost”生成错误链接，这里不提供硬编码默认值；
     * 未配置时将拒绝签发 activationLink。</p>
     */
    private String activationBaseUrl = "";

    /**
     * 是否在注册响应中回传激活链接（仅建议用于本地/测试）。
     */
    private boolean exposeActivationLink = false;

    private Mail mail = new Mail();

    public String getActivationBaseUrl() {
        return activationBaseUrl;
    }

    public void setActivationBaseUrl(String activationBaseUrl) {
        this.activationBaseUrl = activationBaseUrl;
    }

    public boolean isExposeActivationLink() {
        return exposeActivationLink;
    }

    public void setExposeActivationLink(boolean exposeActivationLink) {
        this.exposeActivationLink = exposeActivationLink;
    }

    public Mail getMail() {
        return mail;
    }

    public void setMail(Mail mail) {
        this.mail = mail;
    }

    public static class Mail {
        /**
         * 是否启用 SMTP 发送（默认关闭；未配置 SMTP 时会自动降级为日志输出）。
         */
        private boolean enabled = false;

        private String from = "no-reply@community.local";
        private String subject = "激活账号";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }
    }
}
