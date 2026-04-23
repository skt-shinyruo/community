package com.nowcoder.community.im.realtime.session;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId");
        }
        Objects.requireNonNull(userId, "userId");
        if (!StringUtils.hasText(workerId)) {
            throw new IllegalArgumentException("workerId");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(CLAIM_SESSION_ID, sessionId.trim())
                .claim(CLAIM_WORKER_ID, workerId.trim())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public TicketClaims decode(String ticket) {
        Jwt jwt = jwtDecoder.decode(ticket);
        requireTokenType(jwt.getClaimAsString(CLAIM_TOKEN_TYPE));
        return new TicketClaims(
                parseUserId(jwt.getSubject()),
                requiredText(jwt.getClaimAsString(CLAIM_SESSION_ID), CLAIM_SESSION_ID),
                requiredText(jwt.getClaimAsString(CLAIM_WORKER_ID), CLAIM_WORKER_ID),
                requiredInstant(jwt.getIssuedAt(), "iat"),
                requiredInstant(jwt.getExpiresAt(), "exp")
        );
    }

    private static void requireTokenType(String tokenType) {
        if (!TOKEN_TYPE.equals(tokenType)) {
            throw new BadJwtException("invalid IM session ticket type");
        }
    }

    private static UUID parseUserId(String subject) {
        String value = requiredText(subject, "sub");
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new BadJwtException("invalid IM session ticket subject", ex);
        }
    }

    private static String requiredText(String value, String claimName) {
        if (!StringUtils.hasText(value)) {
            throw new BadJwtException("missing IM session ticket claim: " + claimName);
        }
        return value.trim();
    }

    private static Instant requiredInstant(Instant value, String claimName) {
        if (value == null) {
            throw new BadJwtException("missing IM session ticket claim: " + claimName);
        }
        return value;
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
