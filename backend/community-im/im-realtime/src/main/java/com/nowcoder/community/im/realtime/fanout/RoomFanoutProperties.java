package com.nowcoder.community.im.realtime.fanout;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

@ConfigurationProperties(prefix = "im.room-fanout")
public class RoomFanoutProperties {

    private String ownerGroupId = "im-realtime-room-fanout-owner";
    private String routedCommandTopic = "im.command.room-fanout-routed";
    private int routedCommandPartitions = 64;
    private Integer workerInboxSlot;
    private String workerInboxSlotMetadataKey = "roomFanoutInboxSlot";
    private Duration workerDirectoryCacheTtl = Duration.ofMillis(500);
    private Duration publishTimeout = Duration.ofSeconds(1);

    public String getOwnerGroupId() {
        return ownerGroupId;
    }

    public void setOwnerGroupId(String ownerGroupId) {
        this.ownerGroupId = ownerGroupId;
    }

    public String getRoutedCommandTopic() {
        return routedCommandTopic;
    }

    public void setRoutedCommandTopic(String routedCommandTopic) {
        this.routedCommandTopic = routedCommandTopic;
    }

    public int getRoutedCommandPartitions() {
        return routedCommandPartitions;
    }

    public void setRoutedCommandPartitions(int routedCommandPartitions) {
        this.routedCommandPartitions = routedCommandPartitions;
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
        return publishTimeout == null ? Duration.ofSeconds(1) : publishTimeout;
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

    public int normalizedRoutedCommandPartitions() {
        if (routedCommandPartitions != 64) {
            throw new IllegalStateException("im.room-fanout.routed-command-partitions must be 64");
        }
        return 64;
    }

    public int normalizedWorkerInboxSlot() {
        int partitions = normalizedRoutedCommandPartitions();
        if (workerInboxSlot == null || workerInboxSlot < 0 || workerInboxSlot >= partitions) {
            throw new IllegalStateException(
                    "im.room-fanout.worker-inbox-slot is required and must be between 0 and " + (partitions - 1)
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
