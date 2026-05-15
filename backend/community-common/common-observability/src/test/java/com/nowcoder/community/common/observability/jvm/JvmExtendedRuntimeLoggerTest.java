package com.nowcoder.community.common.observability.jvm;

import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogTestSupport;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JvmExtendedRuntimeLoggerTest {

    @Test
    void logsDirectMemoryPressureAndClassLoadingSummary() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.jvm-extended-runtime")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            properties.getJvm().setDirectMemoryThresholdPercent(80);
            JvmExtendedRuntimeLogger logger = new JvmExtendedRuntimeLogger(capture.writer(), properties);

            assertThat(logger.logDirectMemoryPressure(79, 100)).isFalse();
            assertThat(logger.logDirectMemoryPressure(81, 100)).isTrue();
            logger.logClassLoadingSummary(1000, 80, 200);

            assertThat(capture.appender().list).hasSize(2);
            assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.EVENT_ACTION, "jvm_direct_memory_pressure")
                    .containsEntry("jvm.memory.area", "direct")
                    .containsEntry("jvm.memory.used.bytes", "81")
                    .containsEntry("jvm.memory.max.bytes", "100")
                    .containsEntry("jvm.memory.used.percent", "81")
                    .containsEntry("threshold.percent", "80");
            assertThat(capture.appender().list.get(1).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.EVENT_ACTION, "jvm_class_loading_summary")
                    .containsEntry("jvm.classes.loaded", "1000")
                    .containsEntry("jvm.classes.unloaded", "80")
                    .containsEntry("jvm.classes.loaded.delta", "200");
        }
    }
}
