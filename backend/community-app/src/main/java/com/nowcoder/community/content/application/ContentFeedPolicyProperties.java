package com.nowcoder.community.content.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "content.feed")
public class ContentFeedPolicyProperties {

    private String hotRankVersion = "hot-v2";
    private boolean latestFallbackEnabled = true;

    public String getHotRankVersion() {
        return hotRankVersion;
    }

    public void setHotRankVersion(String hotRankVersion) {
        this.hotRankVersion = hotRankVersion;
    }

    public boolean isLatestFallbackEnabled() {
        return latestFallbackEnabled;
    }

    public void setLatestFallbackEnabled(boolean latestFallbackEnabled) {
        this.latestFallbackEnabled = latestFallbackEnabled;
    }
}
