package com.nowcoder.community.im.gateway.session;

import com.nowcoder.community.im.common.session.OpenImSessionResponse;
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

    public ImSessionService(
            JwtVerifier jwtVerifier,
            RendezvousWorkerSelector workerSelector,
            SessionTicketCodec sessionTicketCodec,
            ImGatewaySessionProperties properties,
            PublicWsUrlFactory publicWsUrlFactory
    ) {
        this.jwtVerifier = jwtVerifier;
        this.workerSelector = workerSelector;
        this.sessionTicketCodec = sessionTicketCodec;
        this.properties = properties;
        this.publicWsUrlFactory = publicWsUrlFactory;
    }

    public OpenImSessionResponse openSession(String authorizationHeader, ServerHttpRequest request) {
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

        return new OpenImSessionResponse(
                sessionId,
                worker.getId(),
                publicWsUrlFactory.build(request),
                ticket,
                expiresAt.toEpochMilli()
        );
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
}
