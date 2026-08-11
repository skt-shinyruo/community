package com.nowcoder.community.im.gateway.session;

import com.nowcoder.community.im.gateway.observability.ImGatewayMetrics;
import com.nowcoder.community.im.gateway.security.AccessTokenFreshnessVerifier;
import com.nowcoder.community.im.gateway.security.JwtVerifier;
import com.nowcoder.community.im.gateway.shard.RendezvousWorkerSelector;
import com.nowcoder.community.im.ticket.SessionTicketCodec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImSessionServiceTest {

    @Test
    void openSessionShouldFailClosedWhenFreshnessVerifierThrowsSynchronously() {
        AccessTokenFreshnessVerifier verifier = accessToken -> {
            throw new IllegalStateException("owner unavailable");
        };

        assertUnavailable(service(verifier).openSession("Bearer access-token", mock(ServerHttpRequest.class)));
    }

    @Test
    void openSessionShouldFailClosedWhenFreshnessVerifierCompletesEmpty() {
        AccessTokenFreshnessVerifier verifier = accessToken -> Mono.empty();

        assertUnavailable(service(verifier).openSession("Bearer access-token", mock(ServerHttpRequest.class)));
    }

    private static ImSessionService service(AccessTokenFreshnessVerifier verifier) {
        JwtVerifier jwtVerifier = mock(JwtVerifier.class);
        when(jwtVerifier.verify("access-token")).thenReturn(new JwtVerifier.VerifiedJwt(
                UUID.fromString("00000000-0000-7000-8000-000000000001"),
                null
        ));
        return new ImSessionService(
                jwtVerifier,
                verifier,
                mock(RendezvousWorkerSelector.class),
                mock(SessionTicketCodec.class),
                mock(ImGatewaySessionProperties.class),
                mock(PublicWsUrlFactory.class),
                mock(ImGatewayMetrics.class)
        );
    }

    private static void assertUnavailable(Mono<?> result) {
        StepVerifier.create(result)
                .expectErrorMatches(error -> error instanceof ResponseStatusException status
                        && HttpStatus.SERVICE_UNAVAILABLE.equals(status.getStatusCode()))
                .verify();
    }
}
