package com.nowcoder.community.im.realtime.presence;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "im.room-presence")
public class RoomPresenceProperties {

    private String keyPrefix = "im:";
    private Duration ttl = Duration.ofSeconds(30);
    private Duration heartbeatInterval = Duration.ofSeconds(10);

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    Duration normalizedTtl() {
        if (ttl == null || ttl.compareTo(Duration.ofSeconds(5)) < 0) {
            return Duration.ofSeconds(5);
        }
        return ttl;
    }

    Duration normalizedHeartbeatInterval() {
        if (heartbeatInterval == null || heartbeatInterval.compareTo(Duration.ofSeconds(1)) < 0) {
            return Duration.ofSeconds(1);
        }
        return heartbeatInterval;
    }

    String normalizedKeyPrefix() {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return "im:";
        }
        return keyPrefix.trim();
    }
}
