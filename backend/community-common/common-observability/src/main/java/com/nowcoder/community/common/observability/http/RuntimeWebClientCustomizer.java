package com.nowcoder.community.common.observability.http;

import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.Locale;

public class RuntimeWebClientCustomizer implements WebClientCustomizer {

    private final HttpClientRuntimeLogger logger;

    public RuntimeWebClientCustomizer(HttpClientRuntimeLogger logger) {
        this.logger = logger;
    }

    @Override
    public void customize(WebClient.Builder webClientBuilder) {
        webClientBuilder.filter((request, next) -> {
            long startedAtNanos = System.nanoTime();
            String peerService = peerService(request.url());
            String method = request.method().name();
            String uri = request.url().toString();
            return next.exchange(request)
                    .doOnNext(response -> logResponse(peerService, method, uri, startedAtNanos, response))
                    .doOnError(throwable -> logger.logClientError(peerService, method, uri, 0, throwable));
        });
    }

    private void logResponse(
            String peerService,
            String method,
            String uri,
            long startedAtNanos,
            ClientResponse response
    ) {
        int statusCode = response.statusCode().value();
        logger.logSlowRequest(peerService, method, uri, statusCode, elapsedMillis(startedAtNanos));
        if (statusCode >= 400) {
            logger.logClientError(peerService, method, uri, statusCode, null);
        }
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
