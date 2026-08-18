package com.nowcoder.community.notice.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notice")
public class NoticePolicyProperties {

    private boolean projectionEnabled = true;
    private final Channels channels = new Channels();

    public Channels getChannels() {
        return channels;
    }

    public boolean isProjectionEnabled() {
        return projectionEnabled;
    }

    public void setProjectionEnabled(boolean projectionEnabled) {
        this.projectionEnabled = projectionEnabled;
    }

    public static class Channels {

        private boolean inAppEnabled = true;

        public boolean isInAppEnabled() {
            return inAppEnabled;
        }

        public void setInAppEnabled(boolean inAppEnabled) {
            this.inAppEnabled = inAppEnabled;
        }
    }

}
