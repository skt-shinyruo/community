package com.nowcoder.community.common.observability.http;

import com.nowcoder.community.common.observability.logging.RuntimeLogEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogSanitizer;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;

public class HttpClientRuntimeLogger {

    private final RuntimeLogWriter logWriter;
    private final RuntimeLoggingProperties properties;

    public HttpClientRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        this.logWriter = logWriter;
        this.properties = properties;
    }

    public boolean logSlowRequest(String peerService, String method, String uri, int statusCode, long durationMs) {
        long threshold = properties.getHttpClient().getSlowRequestThresholdMs();
        if (durationMs < threshold) {
            return false;
        }
        logWriter.warn(RuntimeLogEvent.builder("external_http", "http_client_slow", "threshold", "http client slow")
                .field("peer.service", RuntimeLogSanitizer.operation(peerService))
                .field("http.request.method", RuntimeLogSanitizer.uppercaseOperation(method))
                .field("url.path", RuntimeLogSanitizer.pathTemplate(uri))
                .field("http.response.status_code", statusCode)
                .field(RuntimeLogFields.DURATION_MS, durationMs)
                .field(RuntimeLogFields.THRESHOLD_MS, threshold)
                .build());
        return true;
    }

    public void logClientError(String peerService, String method, String uri, int statusCode, Throwable throwable) {
        RuntimeLogEvent.Builder builder = RuntimeLogEvent.builder("external_http", "http_client_error", "failure", "http client error")
                .field("peer.service", RuntimeLogSanitizer.operation(peerService))
                .field("http.request.method", RuntimeLogSanitizer.uppercaseOperation(method))
                .field("url.path", RuntimeLogSanitizer.pathTemplate(uri))
                .field("http.response.status_code", statusCode);
        if (throwable != null) {
            builder.field(RuntimeLogFields.ERROR_TYPE, throwable.getClass().getName());
        }
        logWriter.warn(builder.build());
    }
}
