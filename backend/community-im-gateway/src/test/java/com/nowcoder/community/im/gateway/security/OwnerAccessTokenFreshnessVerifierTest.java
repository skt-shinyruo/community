package com.nowcoder.community.im.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerAccessTokenFreshnessVerifierTest {

    @Test
    void verifyShouldForwardTheAccessTokenToTheAuthoritativeMeEndpoint() {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    path.set(request.url().getPath());
                    authorization.set(request.headers().getFirst(HttpHeaders.AUTHORIZATION));
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
                .build();

        StepVerifier.create(verifier(webClient).verify("access-token"))
                .expectNext(AccessTokenFreshnessVerifier.Decision.FRESH)
                .verifyComplete();

        assertThat(path).hasValue("/api/auth/me");
        assertThat(authorization).hasValue("Bearer access-token");
    }

    @Test
    void verifyShouldMapOwnerRejectionsAndFailuresToFailClosedDecisions() {
        assertDecision(HttpStatus.UNAUTHORIZED, AccessTokenFreshnessVerifier.Decision.STALE);
        assertDecision(HttpStatus.FORBIDDEN, AccessTokenFreshnessVerifier.Decision.DENIED);
        assertDecision(HttpStatus.INTERNAL_SERVER_ERROR, AccessTokenFreshnessVerifier.Decision.UNAVAILABLE);

        WebClient failedClient = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new IllegalStateException("owner unavailable")))
                .build();
        StepVerifier.create(verifier(failedClient).verify("access-token"))
                .expectNext(AccessTokenFreshnessVerifier.Decision.UNAVAILABLE)
                .verifyComplete();
    }

    @Test
    void verifyShouldFailClosedWhenTheOwnerExceedsTheRequestBudget() {
        AccessTokenFreshnessProperties properties = new AccessTokenFreshnessProperties();
        properties.setRequestTimeout(Duration.ofMillis(10));
        WebClient hangingClient = WebClient.builder()
                .exchangeFunction(request -> Mono.never())
                .build();

        StepVerifier.create(new OwnerAccessTokenFreshnessVerifier(hangingClient, properties).verify("access-token"))
                .expectNext(AccessTokenFreshnessVerifier.Decision.UNAVAILABLE)
                .verifyComplete();
    }

    private static void assertDecision(
            HttpStatus status,
            AccessTokenFreshnessVerifier.Decision expected
    ) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(status).build()))
                .build();

        StepVerifier.create(verifier(webClient).verify("access-token"))
                .expectNext(expected)
                .verifyComplete();
    }

    private static OwnerAccessTokenFreshnessVerifier verifier(WebClient webClient) {
        return new OwnerAccessTokenFreshnessVerifier(webClient, new AccessTokenFreshnessProperties());
    }
}
