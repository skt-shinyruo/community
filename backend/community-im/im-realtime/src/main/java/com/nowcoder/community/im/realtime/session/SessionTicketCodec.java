package com.nowcoder.community.im.realtime.session;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class SessionTicketCodec {

    private static final String CLAIM_SESSION_ID = "sid";
    private static final String CLAIM_WORKER_ID = "wid";
    private static final String CLAIM_TOKEN_TYPE = "typ";
    private static final String CLAIM_ISSUER = "iss";
    private static final String CLAIM_AUDIENCE = "aud";
    private static final String CLAIM_SUBJECT = "sub";
    private static final String CLAIM_ISSUED_AT = "iat";
    private static final String CLAIM_EXPIRES_AT = "exp";
    private static final String TOKEN_TYPE = "im-session-ticket";
    private static final MappedJwtClaimSetConverter DEFAULT_CLAIM_SET_CONVERTER =
            MappedJwtClaimSetConverter.withDefaults(Map.of());

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final String issuer;
    private final String audience;

    public SessionTicketCodec(ImSessionTicketProperties ticketProperties, SecretKey secretKey) {
        Objects.requireNonNull(ticketProperties, "ticketProperties");
        Objects.requireNonNull(secretKey, "secretKey");
        this.issuer = ticketProperties.requiredIssuer();
        this.audience = ticketProperties.requiredAudience();
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setClaimSetConverter(SessionTicketCodec::convertClaimSet);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                audienceValidator(audience),
                ticketTypeValidator()
        ));
        this.jwtDecoder = decoder;
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
                .audience(List.of(audience))
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
                requiredInstant(jwt.getIssuedAt(), CLAIM_ISSUED_AT),
                requiredInstant(jwt.getExpiresAt(), CLAIM_EXPIRES_AT)
        );
    }

    private static Map<String, Object> convertClaimSet(Map<String, Object> claims) {
        requireRawTextClaim(claims, CLAIM_ISSUER);
        requireRawAudienceClaim(claims);
        requireRawTextClaim(claims, CLAIM_TOKEN_TYPE);
        requireRawTextClaim(claims, CLAIM_SUBJECT);
        requireRawTextClaim(claims, CLAIM_SESSION_ID);
        requireRawTextClaim(claims, CLAIM_WORKER_ID);
        requireRawDateClaim(claims, CLAIM_ISSUED_AT);
        requireRawDateClaim(claims, CLAIM_EXPIRES_AT);
        return DEFAULT_CLAIM_SET_CONVERTER.convert(claims);
    }

    private static void requireRawTextClaim(Map<String, Object> claims, String claimName) {
        if (!(claims.get(claimName) instanceof String)) {
            throw invalidRawClaim(claimName);
        }
    }

    private static void requireRawAudienceClaim(Map<String, Object> claims) {
        Object value = claims.get(CLAIM_AUDIENCE);
        if (!(value instanceof List<?> values)
                || values.stream().anyMatch(element -> !(element instanceof String))) {
            throw invalidRawClaim(CLAIM_AUDIENCE);
        }
    }

    private static void requireRawDateClaim(Map<String, Object> claims, String claimName) {
        if (!(claims.get(claimName) instanceof Date)) {
            throw invalidRawClaim(claimName);
        }
    }

    private static BadJwtException invalidRawClaim(String claimName) {
        return new BadJwtException("invalid IM session ticket claim: " + claimName);
    }

    private static void requireTokenType(String tokenType) {
        if (!TOKEN_TYPE.equals(tokenType)) {
            throw new BadJwtException("invalid IM session ticket type");
        }
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String expectedAudience) {
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(expectedAudience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token",
                        "invalid IM session ticket audience",
                        null
                ));
    }

    private static OAuth2TokenValidator<Jwt> ticketTypeValidator() {
        return jwt -> TOKEN_TYPE.equals(jwt.getClaimAsString(CLAIM_TOKEN_TYPE))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token",
                        "invalid IM session ticket type",
                        null
                ));
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
