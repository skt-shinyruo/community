package com.nowcoder.community.im.realtime.fanout;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

@ConfigurationProperties(prefix = "im.room-fanout")
public class RoomFanoutProperties {

    private String mode = "legacy";
    private String ownerGroupId = "im-realtime-room-fanout-owner";
    private Duration ownerFlushInterval = Duration.ofMillis(50);
    private String targetPath = "/internal/im/realtime/fanout/room";
    private Duration targetTimeout = Duration.ofMillis(1000);
    private Duration workerDirectoryCacheTtl = Duration.ofMillis(500);

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getOwnerGroupId() {
        return ownerGroupId;
    }

    public void setOwnerGroupId(String ownerGroupId) {
        this.ownerGroupId = ownerGroupId;
    }

    public Duration getOwnerFlushInterval() {
        return ownerFlushInterval;
    }

    public void setOwnerFlushInterval(Duration ownerFlushInterval) {
        this.ownerFlushInterval = ownerFlushInterval;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public Duration getTargetTimeout() {
        return targetTimeout;
    }

    public void setTargetTimeout(Duration targetTimeout) {
        this.targetTimeout = targetTimeout;
    }

    public Duration getWorkerDirectoryCacheTtl() {
        return workerDirectoryCacheTtl;
    }

    public void setWorkerDirectoryCacheTtl(Duration workerDirectoryCacheTtl) {
        this.workerDirectoryCacheTtl = workerDirectoryCacheTtl;
    }

    public boolean isRoutedMode() {
        return "routed".equalsIgnoreCase(normalizedMode());
    }

    public boolean isShadowMode() {
        return "shadow".equalsIgnoreCase(normalizedMode());
    }

    public Duration normalizedOwnerFlushInterval() {
        if (ownerFlushInterval == null || ownerFlushInterval.compareTo(Duration.ofMillis(10)) < 0) {
            return Duration.ofMillis(10);
        }
        if (ownerFlushInterval.compareTo(Duration.ofSeconds(1)) > 0) {
            return Duration.ofSeconds(1);
        }
        return ownerFlushInterval;
    }

    public Duration normalizedTargetTimeout() {
        if (targetTimeout == null || targetTimeout.compareTo(Duration.ofMillis(50)) < 0) {
            return Duration.ofMillis(50);
        }
        return targetTimeout;
    }

    public Duration normalizedWorkerDirectoryCacheTtl() {
        if (workerDirectoryCacheTtl == null || workerDirectoryCacheTtl.isNegative()) {
            return Duration.ZERO;
        }
        return workerDirectoryCacheTtl;
    }

    public String normalizedTargetPath() {
        if (!StringUtils.hasText(targetPath)) {
            return "/internal/im/realtime/fanout/room";
        }
        String trimmed = targetPath.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private String normalizedMode() {
        return StringUtils.hasText(mode) ? mode.trim() : "legacy";
    }
}
