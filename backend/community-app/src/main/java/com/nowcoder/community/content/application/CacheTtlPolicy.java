package com.nowcoder.community.content.application;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.CRC32;

@Component
public class CacheTtlPolicy {

    private final ContentHotPathProperties properties;

    public CacheTtlPolicy(ContentHotPathProperties properties) {
        this.properties = properties == null ? new ContentHotPathProperties() : properties;
    }

    public Duration jitteredTtl(String cacheKey, Duration baseTtl) {
        if (baseTtl == null || baseTtl.isZero() || baseTtl.isNegative()) {
            return Duration.ofSeconds(1L);
        }
        Duration safeBase = baseTtl;
        Duration jitter = properties.getCache().ttlJitter();
        long jitterSeconds = jitter.toSeconds();
        if (jitterSeconds <= 0L || !StringUtils.hasText(cacheKey)) {
            return safeBase;
        }
        long offset = stablePositiveHash(cacheKey.trim()) % (jitterSeconds + 1L);
        return safeBase.plusSeconds(offset);
    }

    private static Duration positive(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            return Duration.ofSeconds(1L);
        }
        return value;
    }

    private static long stablePositiveHash(String value) {
        CRC32 crc32 = new CRC32();
        crc32.update(value.getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }
}
