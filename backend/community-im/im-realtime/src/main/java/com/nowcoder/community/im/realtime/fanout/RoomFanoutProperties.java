package com.nowcoder.community.im.realtime.fanout;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

@ConfigurationProperties(prefix = "im.room-fanout")
public class RoomFanoutProperties {

    /** Fixed partition count of the routed command topic; must match topic provisioning. */
    public static final int ROUTED_COMMAND_PARTITIONS = 64;

    private String routedCommandTopic = "im.command.room-fanout-routed";
    private Integer workerInboxSlot;
    private String workerInboxSlotMetadataKey = "roomFanoutInboxSlot";
    private Duration workerDirectoryCacheTtl = Duration.ofMillis(500);
    private Duration publishTimeout = Duration.ofSeconds(1);

    public String getRoutedCommandTopic() {
        return routedCommandTopic;
    }

    public void setRoutedCommandTopic(String routedCommandTopic) {
        this.routedCommandTopic = routedCommandTopic;
    }

    public Integer getWorkerInboxSlot() {
        return workerInboxSlot;
    }

    public void setWorkerInboxSlot(Integer workerInboxSlot) {
        this.workerInboxSlot = workerInboxSlot;
    }

    public String getWorkerInboxSlotMetadataKey() {
        return workerInboxSlotMetadataKey;
    }

    public void setWorkerInboxSlotMetadataKey(String workerInboxSlotMetadataKey) {
        this.workerInboxSlotMetadataKey = workerInboxSlotMetadataKey;
    }

    public Duration getWorkerDirectoryCacheTtl() {
        return workerDirectoryCacheTtl;
    }

    public void setWorkerDirectoryCacheTtl(Duration workerDirectoryCacheTtl) {
        this.workerDirectoryCacheTtl = workerDirectoryCacheTtl;
    }

    public Duration getPublishTimeout() {
        return publishTimeout;
    }

    public void setPublishTimeout(Duration publishTimeout) {
        this.publishTimeout = publishTimeout;
    }

    public Duration normalizedPublishTimeout() {
        if (publishTimeout == null) {
            return Duration.ofSeconds(1);
        }
        if (publishTimeout.isZero() || publishTimeout.isNegative()) {
            throw new IllegalStateException("im.room-fanout.publish-timeout must be a positive duration");
        }
        return publishTimeout;
    }

    public Duration normalizedWorkerDirectoryCacheTtl() {
        if (workerDirectoryCacheTtl == null || workerDirectoryCacheTtl.isNegative()) {
            return Duration.ZERO;
        }
        return workerDirectoryCacheTtl;
    }

    public String normalizedRoutedCommandTopic() {
        if (!StringUtils.hasText(routedCommandTopic)) {
            return "im.command.room-fanout-routed";
        }
        return routedCommandTopic.trim();
    }

    public int normalizedWorkerInboxSlot() {
        if (workerInboxSlot == null || workerInboxSlot < 0 || workerInboxSlot >= ROUTED_COMMAND_PARTITIONS) {
            throw new IllegalStateException(
                    "im.room-fanout.worker-inbox-slot is required and must be between 0 and " + (ROUTED_COMMAND_PARTITIONS - 1)
            );
        }
        return workerInboxSlot;
    }

    public String normalizedWorkerInboxSlotMetadataKey() {
        return StringUtils.hasText(workerInboxSlotMetadataKey)
                ? workerInboxSlotMetadataKey.trim()
                : "roomFanoutInboxSlot";
    }
}
