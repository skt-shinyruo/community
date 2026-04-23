package com.nowcoder.community.im.realtime.session;

import com.nowcoder.community.im.common.session.OpenImSessionResponse;
import com.nowcoder.community.im.realtime.projection.ProjectionSyncCoordinator;
import com.nowcoder.community.im.realtime.security.JwtVerifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class ImSessionService {

    private final JwtVerifier jwtVerifier;
    private final ProjectionSyncCoordinator projectionSyncCoordinator;
    private final RendezvousWorkerSelector workerSelector;
    private final SessionTicketCodec sessionTicketCodec;
    private final ImSessionProperties properties;

    public ImSessionService(
            JwtVerifier jwtVerifier,
            ProjectionSyncCoordinator projectionSyncCoordinator,
            RendezvousWorkerSelector workerSelector,
            SessionTicketCodec sessionTicketCodec,
            ImSessionProperties properties
    ) {
        this.jwtVerifier = jwtVerifier;
        this.projectionSyncCoordinator = projectionSyncCoordinator;
        this.workerSelector = workerSelector;
        this.sessionTicketCodec = sessionTicketCodec;
        this.properties = properties;
    }

    public OpenImSessionResponse openSession(String authorizationHeader) {
        projectionSyncCoordinator.requireReady();

        String accessToken = extractBearerToken(authorizationHeader);
        JwtVerifier.VerifiedJwt verified;
        try {
            verified = jwtVerifier.verify(accessToken);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid bearer token", ex);
        }

        RendezvousWorkerSelector.SelectedWorker worker = workerSelector.select(verified.userId());

        String sessionId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(properties.getTicketTtl());
        String ticket = sessionTicketCodec.encode(sessionId, verified.userId(), worker.workerId(), expiresAt);

        return new OpenImSessionResponse(
                sessionId,
                worker.workerId(),
                worker.wsUrl(),
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
