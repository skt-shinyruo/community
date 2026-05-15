package com.nowcoder.community.common.observability.job;

import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogTestSupport;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledJobRuntimeLoggerTest {

    @Test
    void logsSlowSkippedAndErrorScheduledJobs() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.job-runtime")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            properties.getJobs().setSlowJobThresholdMs(1000);
            ScheduledJobRuntimeLogger logger = new ScheduledJobRuntimeLogger(capture.writer(), properties);

            assertThat(logger.logSlowJob("refresh-token-cleanup", 999)).isFalse();
            assertThat(logger.logSlowJob("refresh-token-cleanup", 1001)).isTrue();
            logger.logSkipped("refresh-token-cleanup", "disabled");
            logger.logError("refresh-token-cleanup", new IllegalStateException("failed"));

            assertThat(capture.appender().list).hasSize(3);
            assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.EVENT_CATEGORY, "job")
                    .containsEntry(RuntimeLogFields.EVENT_ACTION, "scheduled_job_slow")
                    .containsEntry("job.system", "spring-scheduled")
                    .containsEntry("job.name", "refresh-token-cleanup")
                    .containsEntry("duration.ms", "1001");
            assertThat(capture.appender().list.get(1).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.EVENT_ACTION, "scheduled_job_skipped")
                    .containsEntry("job.skip.reason", "disabled");
            assertThat(capture.appender().list.get(2).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.EVENT_ACTION, "scheduled_job_error")
                    .containsEntry("error.type", IllegalStateException.class.getName());
        }
    }
}
