package com.nowcoder.community.common.observability.system;

import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogTestSupport;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessResourceRuntimeLoggerTest {

    @Test
    void logsFdDiskAndCpuPressureAtThresholds() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.process-resource-runtime")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            properties.getSystem().setFdUsageThresholdPercent(80);
            properties.getSystem().setDiskUsageThresholdPercent(90);
            properties.getSystem().setCpuLoadThresholdPercent(85);
            ProcessResourceRuntimeLogger logger = new ProcessResourceRuntimeLogger(capture.writer(), properties);

            assertThat(logger.logFdPressure(79, 100)).isFalse();
            assertThat(logger.logFdPressure(81, 100)).isTrue();
            assertThat(logger.logDiskPressure("/", 91, 100)).isTrue();
            assertThat(logger.logCpuLoad(86)).isTrue();

            assertThat(capture.appender().list).hasSize(3);
            assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.COMMUNITY_CATEGORY, "runtime")
                    .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "process_fd_pressure")
                    .containsEntry("process.fd.used.percent", "81")
                    .containsEntry("threshold.percent", "80");
            assertThat(capture.appender().list.get(1).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "disk_space_pressure")
                    .containsEntry("filesystem.mount", "/")
                    .containsEntry("disk.used.percent", "91");
            assertThat(capture.appender().list.get(2).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "cpu_load_threshold")
                    .containsEntry("process.cpu.load.percent", "86");
        }
    }
}
