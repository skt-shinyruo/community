package com.nowcoder.community.im.realtime.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "im.clients")
public class ImServiceClientProperties {

    private String communityServiceId = "community-app";
    private String imCoreServiceId = "im-core";
    private String membershipSnapshotServiceId = "im-core";
    private String policySnapshotServiceId = "community-app";
    private long snapshotTimeoutMs = 3000;
    private String internalScope = "im.realtime.internal";

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

    public String getMembershipSnapshotServiceId() {
        return membershipSnapshotServiceId;
    }

    public void setMembershipSnapshotServiceId(String membershipSnapshotServiceId) {
        this.membershipSnapshotServiceId = membershipSnapshotServiceId;
    }

    public String getPolicySnapshotServiceId() {
        return policySnapshotServiceId;
    }

    public void setPolicySnapshotServiceId(String policySnapshotServiceId) {
        this.policySnapshotServiceId = policySnapshotServiceId;
    }

    public long getSnapshotTimeoutMs() {
        return snapshotTimeoutMs;
    }

    public void setSnapshotTimeoutMs(long snapshotTimeoutMs) {
        this.snapshotTimeoutMs = snapshotTimeoutMs;
    }

    public String getInternalScope() {
        return internalScope;
    }

    public void setInternalScope(String internalScope) {
        this.internalScope = internalScope;
    }
}
