package com.nowcoder.community.im.realtime.session;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
public class SessionTicketCodec {

    private static final String CLAIM_SESSION_ID = "sid";
    private static final String CLAIM_WORKER_ID = "wid";
    private static final String CLAIM_TOKEN_TYPE = "typ";
    private static final String TOKEN_TYPE = "im-session-ticket";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final String issuer;

    public SessionTicketCodec(JwtProperties jwtProperties, JwtDecoder jwtDecoder) {
        this.jwtEncoder = JwtCodecs.jwtEncoder(jwtProperties);
        this.jwtDecoder = jwtDecoder;
        this.issuer = JwtCodecs.resolvedIssuer(jwtProperties);
    }

    public String encode(String sessionId, UUID userId, String workerId, Instant expiresAt) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(CLAIM_SESSION_ID, String.valueOf(sessionId))
                .claim(CLAIM_WORKER_ID, String.valueOf(workerId))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public TicketClaims decode(String ticket) {
        Jwt jwt = jwtDecoder.decode(ticket);
        return new TicketClaims(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString(CLAIM_SESSION_ID),
                jwt.getClaimAsString(CLAIM_WORKER_ID),
                jwt.getIssuedAt(),
                jwt.getExpiresAt()
        );
    }

    public record TicketClaims(
            UUID userId,
            String sessionId,
            String workerId,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }
}
