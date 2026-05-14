package com.nowcoder.community.common.observability.oss;

import com.nowcoder.community.common.observability.logging.RuntimeLogEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogSanitizer;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;

public class OssRuntimeLogger {

    private final RuntimeLogWriter logWriter;
    private final RuntimeLoggingProperties properties;

    public OssRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        this.logWriter = logWriter;
        this.properties = properties;
    }

    public boolean logSlowOperation(String operation, String bucket, String objectKey, long objectSizeBytes, long durationMs) {
        long threshold = properties.getOss().getSlowOperationThresholdMs();
        if (durationMs < threshold) {
            return false;
        }
        String normalizedOperation = RuntimeLogSanitizer.operation(operation);
        String action = "download".equals(normalizedOperation) ? "oss_download_slow" : "oss_upload_slow";
        logWriter.warn(RuntimeLogEvent.builder("object_storage", action, "threshold", "oss operation slow")
                .field("oss.operation", normalizedOperation)
                .field("oss.bucket", RuntimeLogSanitizer.text(bucket))
                .field("object.size.bucket", RuntimeLogSanitizer.sizeBucket(objectSizeBytes))
                .field(RuntimeLogFields.DURATION_MS, durationMs)
                .field(RuntimeLogFields.THRESHOLD_MS, threshold)
                .build());
        return true;
    }

    public void logClientError(String operation, String bucket, String objectKey, String errorCode, Throwable throwable) {
        RuntimeLogEvent.Builder builder = RuntimeLogEvent.builder("object_storage", "oss_client_error", "failure", "oss client error")
                .field("oss.operation", RuntimeLogSanitizer.operation(operation))
                .field("oss.bucket", RuntimeLogSanitizer.text(bucket))
                .field("error.code", RuntimeLogSanitizer.text(errorCode));
        if (throwable != null) {
            builder.field(RuntimeLogFields.ERROR_TYPE, throwable.getClass().getName());
        }
        logWriter.warn(builder.build());
    }
}
