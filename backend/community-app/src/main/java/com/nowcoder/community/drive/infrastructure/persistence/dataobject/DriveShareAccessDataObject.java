package com.nowcoder.community.drive.infrastructure.persistence.dataobject;

import java.time.Instant;
import java.util.UUID;

public class DriveShareAccessDataObject {

    private UUID accessId;
    private UUID shareId;
    private String visitorFingerprint;
    private boolean success;
    private Instant accessedAt;

    public DriveShareAccessDataObject() {
    }

    public DriveShareAccessDataObject(UUID accessId, UUID shareId, String visitorFingerprint, boolean success, Instant accessedAt) {
        this.accessId = accessId;
        this.shareId = shareId;
        this.visitorFingerprint = visitorFingerprint;
        this.success = success;
        this.accessedAt = accessedAt;
    }

    public UUID getAccessId() {
        return accessId;
    }

    public void setAccessId(UUID accessId) {
        this.accessId = accessId;
    }

    public UUID getShareId() {
        return shareId;
    }

    public void setShareId(UUID shareId) {
        this.shareId = shareId;
    }

    public String getVisitorFingerprint() {
        return visitorFingerprint;
    }

    public void setVisitorFingerprint(String visitorFingerprint) {
        this.visitorFingerprint = visitorFingerprint;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Instant getAccessedAt() {
        return accessedAt;
    }

    public void setAccessedAt(Instant accessedAt) {
        this.accessedAt = accessedAt;
    }
}
