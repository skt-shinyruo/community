package com.nowcoder.community.im.gateway.session;

import com.nowcoder.community.im.common.session.OpenImSessionResponse;
import com.nowcoder.community.im.gateway.observability.ImGatewayMetrics;
import com.nowcoder.community.im.gateway.security.JwtVerifier;
import com.nowcoder.community.im.gateway.shard.RendezvousWorkerSelector;
import com.nowcoder.community.im.gateway.shard.WorkerDescriptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class ImSessionService {

    private final JwtVerifier jwtVerifier;
    private final RendezvousWorkerSelector workerSelector;
    private final SessionTicketCodec sessionTicketCodec;
    private final ImGatewaySessionProperties properties;
    private final PublicWsUrlFactory publicWsUrlFactory;
    private final ImGatewayMetrics metrics;

    public ImSessionService(
            JwtVerifier jwtVerifier,
            RendezvousWorkerSelector workerSelector,
            SessionTicketCodec sessionTicketCodec,
            ImGatewaySessionProperties properties,
            PublicWsUrlFactory publicWsUrlFactory,
            ImGatewayMetrics metrics
    ) {
        this.jwtVerifier = jwtVerifier;
        this.workerSelector = workerSelector;
        this.sessionTicketCodec = sessionTicketCodec;
        this.properties = properties;
        this.publicWsUrlFactory = publicWsUrlFactory;
        this.metrics = metrics;
    }

    public OpenImSessionResponse openSession(String authorizationHeader, ServerHttpRequest request) {
        try {
            String accessToken = extractBearerToken(authorizationHeader);
            JwtVerifier.VerifiedJwt verified;
            try {
                verified = jwtVerifier.verify(accessToken);
            } catch (RuntimeException ex) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid bearer token", ex);
            }

            WorkerDescriptor worker = workerSelector.select(verified.userId());
            String sessionId = UUID.randomUUID().toString();
            Instant expiresAt = Instant.now().plus(properties.getSession().getTicketTtl());
            String ticket = sessionTicketCodec.encode(sessionId, verified.userId(), worker.getId(), expiresAt);
            OpenImSessionResponse response = new OpenImSessionResponse(
                    sessionId,
                    publicWsUrlFactory.build(request),
                    ticket,
                    expiresAt.toEpochMilli()
            );
            metrics.sessionOpened();
            return response;
        } catch (ResponseStatusException ex) {
            metrics.sessionFailed(sessionFailureReason(ex));
            throw ex;
        } catch (RuntimeException ex) {
            metrics.sessionFailed("unexpected");
            throw ex;
        }
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

    private static String sessionFailureReason(ResponseStatusException ex) {
        if (HttpStatus.UNAUTHORIZED.equals(ex.getStatusCode())) {
            return "invalid_token";
        }
        if (HttpStatus.SERVICE_UNAVAILABLE.equals(ex.getStatusCode())) {
            return "no_workers";
        }
        return "unexpected";
    }
}
