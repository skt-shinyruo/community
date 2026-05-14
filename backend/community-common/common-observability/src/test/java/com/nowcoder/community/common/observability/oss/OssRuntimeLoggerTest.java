package com.nowcoder.community.common.observability.oss;

import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogTestSupport;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OssRuntimeLoggerTest {

    @Test
    void logsSlowUploadSlowDownloadAndClientErrorsWithoutObjectKeys() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.oss-runtime")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            properties.getOss().setSlowOperationThresholdMs(500);
            OssRuntimeLogger logger = new OssRuntimeLogger(capture.writer(), properties);

            assertThat(logger.logSlowOperation("upload", "avatar-bucket", "user/secret/avatar.png", 1024, 400)).isFalse();
            assertThat(logger.logSlowOperation("upload", "avatar-bucket", "user/secret/avatar.png", 1024, 501)).isTrue();
            assertThat(logger.logSlowOperation("download", "avatar-bucket", "user/secret/avatar.png", 9_999_999, 600)).isTrue();
            logger.logClientError("upload", "avatar-bucket", "user/secret/avatar.png", "503", new IllegalStateException("bad key user/secret/avatar.png"));

            assertThat(capture.appender().list).hasSize(3);
            assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.COMMUNITY_CATEGORY, "object_storage")
                    .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "oss_upload_slow")
                    .containsEntry("oss.bucket", "avatar-bucket")
                    .containsEntry("object.size.bucket", "1KB-1MB")
                    .containsEntry("duration.ms", "501");
            assertThat(capture.appender().list.get(1).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "oss_download_slow")
                    .containsEntry("object.size.bucket", "1MB-10MB");
            assertThat(capture.appender().list.get(2).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "oss_client_error")
                    .containsEntry("error.code", "503");
            capture.appender().list.forEach(event -> {
                assertThat(event.getMDCPropertyMap()).doesNotContainKey("oss.object.key");
                assertThat(event.getFormattedMessage()).doesNotContain("user/secret/avatar.png");
            });
        }
    }
}
