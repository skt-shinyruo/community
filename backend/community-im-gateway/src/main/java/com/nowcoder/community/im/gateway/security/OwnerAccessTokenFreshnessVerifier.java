package com.nowcoder.community.im.gateway.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class OwnerAccessTokenFreshnessVerifier implements AccessTokenFreshnessVerifier {

    private final WebClient webClient;
    private final Duration requestTimeout;

    public OwnerAccessTokenFreshnessVerifier(
            @Qualifier("imAccessTokenFreshnessWebClient") WebClient webClient,
            AccessTokenFreshnessProperties properties
    ) {
        this.webClient = webClient;
        this.requestTimeout = properties.normalizedRequestTimeout();
    }

    @Override
    public Mono<Decision> verify(String accessToken) {
        return webClient.get()
                .uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchangeToMono(response -> Mono.just(decision(response.statusCode().value())))
                .timeout(requestTimeout)
                .onErrorReturn(Decision.UNAVAILABLE);
    }

    private Decision decision(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return Decision.FRESH;
        }
        if (statusCode == HttpStatus.UNAUTHORIZED.value()) {
            return Decision.STALE;
        }
        if (statusCode == HttpStatus.FORBIDDEN.value()) {
            return Decision.DENIED;
        }
        return Decision.UNAVAILABLE;
    }
}
