package com.nowcoder.community.im.gateway.session;

import com.nowcoder.community.im.common.session.OpenImSessionResponse;
import com.nowcoder.community.im.gateway.observability.ImGatewayMetrics;
import com.nowcoder.community.im.gateway.security.AccessTokenFreshnessVerifier;
import com.nowcoder.community.im.gateway.security.JwtVerifier;
import com.nowcoder.community.im.gateway.shard.RendezvousWorkerSelector;
import com.nowcoder.community.im.gateway.shard.WorkerDescriptor;
import com.nowcoder.community.im.ticket.SessionTicketCodec;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
public class ImSessionService {

    private final JwtVerifier jwtVerifier;
    private final AccessTokenFreshnessVerifier accessTokenFreshnessVerifier;
    private final RendezvousWorkerSelector workerSelector;
    private final SessionTicketCodec sessionTicketCodec;
    private final ImGatewaySessionProperties properties;
    private final PublicWsUrlFactory publicWsUrlFactory;
    private final ImGatewayMetrics metrics;

    public ImSessionService(
            JwtVerifier jwtVerifier,
            AccessTokenFreshnessVerifier accessTokenFreshnessVerifier,
            RendezvousWorkerSelector workerSelector,
            SessionTicketCodec sessionTicketCodec,
            ImGatewaySessionProperties properties,
            PublicWsUrlFactory publicWsUrlFactory,
            ImGatewayMetrics metrics
    ) {
        this.jwtVerifier = jwtVerifier;
        this.accessTokenFreshnessVerifier = accessTokenFreshnessVerifier;
        this.workerSelector = workerSelector;
        this.sessionTicketCodec = sessionTicketCodec;
        this.properties = properties;
        this.publicWsUrlFactory = publicWsUrlFactory;
        this.metrics = metrics;
    }

    public Mono<OpenImSessionResponse> openSession(String authorizationHeader, ServerHttpRequest request) {
        return Mono.defer(() -> {
            String accessToken = extractBearerToken(authorizationHeader);
            JwtVerifier.VerifiedJwt verified;
            try {
                verified = jwtVerifier.verify(accessToken);
            } catch (RuntimeException ex) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid bearer token", ex);
            }

            return Mono.defer(() -> accessTokenFreshnessVerifier.verify(accessToken))
                    .defaultIfEmpty(AccessTokenFreshnessVerifier.Decision.UNAVAILABLE)
                    .onErrorReturn(AccessTokenFreshnessVerifier.Decision.UNAVAILABLE)
                    .flatMap(decision -> decision == AccessTokenFreshnessVerifier.Decision.FRESH
                            ? Mono.just(openVerifiedSession(verified.userId(), request))
                            : Mono.error(freshnessFailure(decision)));
        }).doOnSuccess(response -> metrics.sessionOpened())
                .doOnError(ex -> metrics.sessionFailed(sessionFailureReason(ex)));
    }

    private OpenImSessionResponse openVerifiedSession(UUID userId, ServerHttpRequest request) {
        WorkerDescriptor worker = workerSelector.select(userId);
        String sessionId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(properties.getSession().getTicketTtl());
        String ticket = sessionTicketCodec.encode(sessionId, userId, worker.id(), expiresAt);
        return new OpenImSessionResponse(
                sessionId,
                publicWsUrlFactory.build(request),
                ticket,
                expiresAt.toEpochMilli()
        );
    }

    private ResponseStatusException freshnessFailure(AccessTokenFreshnessVerifier.Decision decision) {
        return switch (decision) {
            case STALE -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "stale bearer token");
            case DENIED -> new ResponseStatusException(HttpStatus.FORBIDDEN, "bearer token owner denied");
            case UNAVAILABLE -> new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "token freshness owner unavailable"
            );
            case FRESH -> throw new IllegalArgumentException("fresh decision is not a failure");
        };
    }

    private static String extractBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing bearer token");
        }
        String value = authorizationHeader.trim();
        if (!value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing bearer token");
        }
        String token = value.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing bearer token");
        }
        return token;
    }

    private static String sessionFailureReason(Throwable failure) {
        if (!(failure instanceof ResponseStatusException ex)) {
            return "unexpected";
        }
        if (HttpStatus.UNAUTHORIZED.equals(ex.getStatusCode())) {
            return "invalid_token";
        }
        if (HttpStatus.FORBIDDEN.equals(ex.getStatusCode())) {
            return "token_denied";
        }
        if (HttpStatus.SERVICE_UNAVAILABLE.equals(ex.getStatusCode())) {
            return "token freshness owner unavailable".equals(ex.getReason())
                    ? "freshness_unavailable"
                    : "no_workers";
        }
        return "unexpected";
    }
}
