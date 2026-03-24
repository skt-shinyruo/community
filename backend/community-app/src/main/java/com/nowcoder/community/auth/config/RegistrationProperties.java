package com.nowcoder.community.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.registration")
public class RegistrationProperties {

    private PendingUser pendingUser = new PendingUser();
    private Code code = new Code();
    private Mail mail = new Mail();

    public PendingUser getPendingUser() {
        return pendingUser;
    }

    public void setPendingUser(PendingUser pendingUser) {
        this.pendingUser = pendingUser;
    }

    public Code getCode() {
        return code;
    }

    public void setCode(Code code) {
        this.code = code;
    }

    public Mail getMail() {
        return mail;
    }

    public void setMail(Mail mail) {
        this.mail = mail;
    }

    public static class PendingUser {
        /**
         * 未激活注册用户存活时间（秒）；超过后视为过期，可重新注册。
         */
        private int ttlSeconds = 1800;

        /**
         * 是否启用本地 fixed-delay 清理兜底任务。
         */
        private boolean localSchedulerEnabled = true;

        /**
         * 过期未激活用户清理任务的 fixed-delay（毫秒）。
         */
        private long cleanupIntervalMs = 300000;

        public int getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : 1800;
        }

        public boolean isLocalSchedulerEnabled() {
            return localSchedulerEnabled;
        }

        public void setLocalSchedulerEnabled(boolean localSchedulerEnabled) {
            this.localSchedulerEnabled = localSchedulerEnabled;
        }

        public long getCleanupIntervalMs() {
            return cleanupIntervalMs;
        }

        public void setCleanupIntervalMs(long cleanupIntervalMs) {
            this.cleanupIntervalMs = cleanupIntervalMs > 0 ? cleanupIntervalMs : 300000L;
        }
    }

    public static class Code {
        /**
         * 存储方式：redis / memory（测试用）。
         */
        private String store = "redis";

        /**
         * 邮箱验证码有效期（秒）。
         */
        private int ttlSeconds = 600;

        /**
         * 单个验证码允许失败次数；达到阈值后作废并要求重新发送。
         */
        private int maxFailures = 3;

        /**
         * 重发冷却期（秒）。
         */
        private int resendCooldownSeconds = 60;

        /**
         * 是否在响应中回传调试验证码（仅本地/测试联调建议开启）。
         */
        private boolean exposeCode = false;

        public String getStore() {
            return store;
        }

        public void setStore(String store) {
            this.store = store;
        }

        public int getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : 600;
        }

        public int getMaxFailures() {
            return maxFailures;
        }

        public void setMaxFailures(int maxFailures) {
            this.maxFailures = maxFailures > 0 ? maxFailures : 3;
        }

        public int getResendCooldownSeconds() {
            return resendCooldownSeconds;
        }

        public void setResendCooldownSeconds(int resendCooldownSeconds) {
            this.resendCooldownSeconds = Math.max(0, resendCooldownSeconds);
        }

        public boolean isExposeCode() {
            return exposeCode;
        }

        public void setExposeCode(boolean exposeCode) {
            this.exposeCode = exposeCode;
        }
    }

    public static class Mail {
        /**
         * 是否启用 SMTP 发送（默认关闭；未配置 SMTP 时会自动降级为日志输出）。
         */
        private boolean enabled = false;

        private String from = "no-reply@community.local";
        private String subject = "注册验证码";

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
