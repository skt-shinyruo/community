package com.nowcoder.community.notice.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "notice")
public class NoticePolicyProperties {

    private boolean projectionEnabled = true;
    private final Channels channels = new Channels();
    private final Templates templates = new Templates();
    private final Digest digest = new Digest();

    public Channels getChannels() {
        return channels;
    }

    public boolean isProjectionEnabled() {
        return projectionEnabled;
    }

    public void setProjectionEnabled(boolean projectionEnabled) {
        this.projectionEnabled = projectionEnabled;
    }

    public Templates getTemplates() {
        return templates;
    }

    public Digest getDigest() {
        return digest;
    }

    public static class Channels {

        private boolean emailEnabled = true;
        private boolean inAppEnabled = true;

        public boolean isEmailEnabled() {
            return emailEnabled;
        }

        public void setEmailEnabled(boolean emailEnabled) {
            this.emailEnabled = emailEnabled;
        }

        public boolean isInAppEnabled() {
            return inAppEnabled;
        }

        public void setInAppEnabled(boolean inAppEnabled) {
            this.inAppEnabled = inAppEnabled;
        }
    }

    public static class Templates {

        private String senderDisplayName = "Community";
        private String defaultTitle = "Community notification";
        private String defaultLinkPath = "/notifications";

        public String getSenderDisplayName() {
            return senderDisplayName;
        }

        public void setSenderDisplayName(String senderDisplayName) {
            this.senderDisplayName = senderDisplayName;
        }

        public String getDefaultTitle() {
            return defaultTitle;
        }

        public void setDefaultTitle(String defaultTitle) {
            this.defaultTitle = defaultTitle;
        }

        public String getDefaultLinkPath() {
            return defaultLinkPath;
        }

        public void setDefaultLinkPath(String defaultLinkPath) {
            this.defaultLinkPath = defaultLinkPath;
        }
    }

    public static class Digest {

        private boolean enabled = false;
        private Duration window = Duration.ofHours(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window == null || window.isNegative() || window.isZero() ? Duration.ofHours(1) : window;
        }
    }
}
