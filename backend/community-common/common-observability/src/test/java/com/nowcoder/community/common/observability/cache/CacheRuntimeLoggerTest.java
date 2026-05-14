package com.nowcoder.community.common.observability.cache;

import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogTestSupport;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheRuntimeLoggerTest {

    @Test
    void logsLowCacheHitRatioWithoutCacheKeys() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.cache-runtime")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            properties.getCache().setHitRatioThresholdPercent(70);
            CacheRuntimeLogger logger = new CacheRuntimeLogger(capture.writer(), properties);

            assertThat(logger.logHitRatio("local-user-cache", 70, 30)).isFalse();
            assertThat(logger.logHitRatio("local-user-cache", 69, 31)).isTrue();

            assertThat(capture.appender().list).hasSize(1);
            assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.COMMUNITY_CATEGORY, "cache")
                    .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "cache_hit_ratio_low")
                    .containsEntry("cache.name", "local-user-cache")
                    .containsEntry("cache.hit.ratio.percent", "69")
                    .containsEntry("threshold.percent", "70")
                    .doesNotContainKey("cache.key");
        }
    }
}
