package com.nowcoder.community.common.observability.http;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

public class RuntimeRestClientCustomizer implements RestClientCustomizer {

    private final HttpClientRuntimeLogger logger;

    public RuntimeRestClientCustomizer(HttpClientRuntimeLogger logger) {
        this.logger = logger;
    }

    @Override
    public void customize(RestClient.Builder restClientBuilder) {
        restClientBuilder.requestInterceptor((request, body, execution) -> {
            long startedAtNanos = System.nanoTime();
            String peerService = peerService(request.getURI());
            String method = request.getMethod().name();
            String uri = request.getURI().toString();
            try {
                ClientHttpResponse response = execution.execute(request, body);
                int statusCode = response.getStatusCode().value();
                long durationMs = elapsedMillis(startedAtNanos);
                logger.logSlowRequest(peerService, method, uri, statusCode, durationMs);
                if (statusCode >= 400) {
                    logger.logClientError(peerService, method, uri, statusCode, null);
                }
                return response;
            } catch (IOException | RuntimeException ex) {
                logger.logClientError(peerService, method, uri, 0, ex);
                throw ex;
            }
        });
    }

    private String peerService(URI uri) {
        if (uri == null) {
            return "-";
        }
        if (uri.getHost() != null && !uri.getHost().isBlank()) {
            return uri.getHost().toLowerCase(Locale.ROOT);
        }
        String authority = uri.getAuthority();
        return authority == null || authority.isBlank() ? "-" : authority.toLowerCase(Locale.ROOT);
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
