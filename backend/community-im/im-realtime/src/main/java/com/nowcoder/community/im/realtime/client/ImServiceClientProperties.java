package com.nowcoder.community.im.realtime.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "im.clients")
public class ImServiceClientProperties {

    private String communityServiceId = "community-app";
    private String imCoreServiceId = "im-core";

    public String getCommunityServiceId() {
        return communityServiceId;
    }

    public void setCommunityServiceId(String communityServiceId) {
        this.communityServiceId = communityServiceId;
    }

    public String getImCoreServiceId() {
        return imCoreServiceId;
    }

    public void setImCoreServiceId(String imCoreServiceId) {
        this.imCoreServiceId = imCoreServiceId;
    }
}
