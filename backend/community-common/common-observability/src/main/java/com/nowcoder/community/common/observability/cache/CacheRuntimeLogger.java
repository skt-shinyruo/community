package com.nowcoder.community.common.observability.cache;

import com.nowcoder.community.common.observability.logging.RuntimeLogEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogSanitizer;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;

public class CacheRuntimeLogger {

    private final RuntimeLogWriter logWriter;
    private final RuntimeLoggingProperties properties;

    public CacheRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        this.logWriter = logWriter;
        this.properties = properties;
    }

    public boolean logHitRatio(String cacheName, long hitCount, long missCount) {
        long total = hitCount + missCount;
        long ratio = total <= 0 ? 100 : hitCount * 100 / total;
        int threshold = properties.getCache().getHitRatioThresholdPercent();
        if (ratio >= threshold) {
            return false;
        }
        logWriter.warn(RuntimeLogEvent.builder("cache", "cache_hit_ratio_low", "threshold", "cache hit ratio low")
                .field("cache.name", RuntimeLogSanitizer.operation(cacheName))
                .field("cache.hit.count", hitCount)
                .field("cache.miss.count", missCount)
                .field("cache.hit.ratio.percent", ratio)
                .field(RuntimeLogFields.THRESHOLD_PERCENT, threshold)
                .build());
        return true;
    }
}
