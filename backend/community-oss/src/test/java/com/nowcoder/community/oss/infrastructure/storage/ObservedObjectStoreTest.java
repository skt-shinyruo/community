package com.nowcoder.community.oss.infrastructure.storage;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import com.nowcoder.community.common.observability.oss.OssRuntimeLogger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservedObjectStoreTest {

    @Test
    void logsObjectStoreSlowTransfersAndErrorsWithoutObjectKey() {
        try (Capture capture = Capture.open("test.observed-object-store")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            properties.getOss().setSlowOperationThresholdMs(0);
            OssRuntimeLogger logger = new OssRuntimeLogger(capture.writer(), properties);
            ObjectStore delegate = new StubObjectStore();
            ObjectStore objectStore = new ObservedObjectStore(delegate, logger);

            objectStore.put("avatar-bucket", "user/secret/avatar.png", new ByteArrayInputStream(new byte[0]), 1024, "image/png");
            objectStore.get("avatar-bucket", "user/secret/avatar.png");
            assertThatThrownBy(() -> objectStore.delete("avatar-bucket", "user/secret/avatar.png"))
                    .isInstanceOf(ObjectStoreException.class);

            assertThat(capture.appender().list)
                    .anySatisfy(event -> assertThat(event.getMDCPropertyMap())
                            .containsEntry(RuntimeLogFields.EVENT_ACTION, "oss_upload_slow")
                            .containsEntry("oss.bucket", "avatar-bucket")
                            .containsEntry("object.size.bucket", "1KB-1MB"))
                    .anySatisfy(event -> assertThat(event.getMDCPropertyMap())
                            .containsEntry(RuntimeLogFields.EVENT_ACTION, "oss_download_slow")
                            .containsEntry("oss.bucket", "avatar-bucket")
                            .containsEntry("object.size.bucket", "1MB-10MB"))
                    .anySatisfy(event -> assertThat(event.getMDCPropertyMap())
                            .containsEntry(RuntimeLogFields.EVENT_ACTION, "oss_client_error")
                            .containsEntry("oss.operation", "delete")
                            .containsEntry("error.code", "OBJECT_STORE_ERROR"));
            capture.appender().list.forEach(event -> {
                assertThat(event.getMDCPropertyMap()).doesNotContainKey("oss.object.key");
                assertThat(event.getFormattedMessage()).doesNotContain("user/secret/avatar.png");
            });
        }
    }

    private static final class StubObjectStore implements ObjectStore {

        @Override
        public void put(String bucket, String key, InputStream content, long contentLength, String contentType) {
        }

        @Override
        public Optional<ObjectStoreObject> head(String bucket, String key) {
            return Optional.empty();
        }

        @Override
        public StoredObject get(String bucket, String key) {
            return new StoredObject(new ByteArrayInputStream(new byte[0]), "image/png", 9_999_999);
        }

        @Override
        public void delete(String bucket, String key) {
            throw new ObjectStoreException("delete failed for " + key, null);
        }

        @Override
        public PresignedObjectUrl presignUpload(String bucket, String key, Duration ttl, String contentType) {
            return new PresignedObjectUrl("https://example.test/upload", "PUT", null, null);
        }

        @Override
        public PresignedObjectUrl presignDownload(String bucket, String key, Duration ttl) {
            return new PresignedObjectUrl("https://example.test/download", "GET", null, null);
        }
    }

    private static final class Capture implements AutoCloseable {

        private final Logger logger;
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        private final RuntimeLogWriter writer;

        private Capture(String loggerName) {
            this.logger = (Logger) LoggerFactory.getLogger(loggerName);
            this.writer = new RuntimeLogWriter(logger);
            appender.start();
            logger.addAppender(appender);
        }

        static Capture open(String loggerName) {
            return new Capture(loggerName);
        }

        RuntimeLogWriter writer() {
            return writer;
        }

        ListAppender<ILoggingEvent> appender() {
            return appender;
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
            MDC.clear();
        }
    }
}
