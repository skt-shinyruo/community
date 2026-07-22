package com.nowcoder.community.im.gateway.session;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionTicketCodecTest {

    private static final String ACCESS_SECRET = "access-token-test-secret-distinct-1234567890";
    private static final String TICKET_SECRET = "im-session-ticket-test-secret-distinct-1234567890";
    private static final String OTHER_TICKET_SECRET = "other-session-ticket-test-secret-distinct-1234567890";
    private static final String TICKET_ISSUER = "community-im-gateway";
    private static final String TICKET_AUDIENCE = "im-realtime";
    private static final String TICKET_TYPE = "im-session-ticket";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000123");

    @Test
    void encodeAndDecode_shouldRoundTripDedicatedTicketClaims() {
        SessionTicketCodec codec = codec(ticketProperties(TICKET_SECRET));
        Instant expiresAt = Instant.now().plusSeconds(90).truncatedTo(ChronoUnit.SECONDS);

        String ticket = codec.encode("sess-1", USER_ID, "worker-a", expiresAt);
        Jwt raw = rawDecoder(TICKET_SECRET).decode(ticket);
        SessionTicketCodec.TicketClaims claims = codec.decode(ticket);

        assertThat(raw.getClaims()).containsOnlyKeys("iss", "aud", "typ", "sub", "sid", "wid", "iat", "exp");
        assertThat(raw.getClaimAsString("iss")).isEqualTo(TICKET_ISSUER);
        assertThat(raw.getAudience()).containsExactly(TICKET_AUDIENCE);
        assertThat(raw.getClaimAsString("typ")).isEqualTo(TICKET_TYPE);
        assertThat(raw.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(raw.getClaimAsString("sid")).isEqualTo("sess-1");
        assertThat(raw.getClaimAsString("wid")).isEqualTo("worker-a");
        assertThat(raw.getIssuedAt()).isNotNull();
        assertThat(raw.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(claims.userId()).isEqualTo(USER_ID);
        assertThat(claims.sessionId()).isEqualTo("sess-1");
        assertThat(claims.workerId()).isEqualTo("worker-a");
        assertThat(claims.issuedAt()).isNotNull();
        assertThat(claims.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void encode_shouldTrimConfiguredIssuerAndAudience() {
        ImSessionTicketProperties properties = ticketProperties(TICKET_SECRET);
        properties.setIssuer("  dedicated-ticket-issuer  ");
        properties.setAudience("  dedicated-ticket-audience  ");
        SessionTicketCodec codec = codec(properties);

        Jwt raw = rawDecoder(TICKET_SECRET).decode(codec.encode(
                "sess-1",
                USER_ID,
                "worker-a",
                Instant.now().plusSeconds(90)
        ));

        assertThat(raw.getClaimAsString("iss")).isEqualTo("dedicated-ticket-issuer");
        assertThat(raw.getAudience()).containsExactly("dedicated-ticket-audience");
    }

    @Test
    void decode_shouldRejectTicketSignedWithWrongSecret() {
        SessionTicketCodec codec = codec(ticketProperties(TICKET_SECRET));
        String ticket = rawTicket(
                OTHER_TICKET_SECRET,
                TICKET_ISSUER,
                List.of(TICKET_AUDIENCE),
                TICKET_TYPE,
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(60),
                true
        );

        assertThatThrownBy(() -> codec.decode(ticket)).isInstanceOf(JwtException.class);
    }

    @Test
    void decode_shouldRejectWrongIssuer() {
        SessionTicketCodec codec = codec(ticketProperties(TICKET_SECRET));
        String ticket = rawTicket(
                TICKET_SECRET,
                "other-issuer",
                List.of(TICKET_AUDIENCE),
                TICKET_TYPE,
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(60),
                true
        );

        assertThatThrownBy(() -> codec.decode(ticket)).isInstanceOf(JwtException.class);
    }

    @Test
    void decode_shouldRejectMissingExpectedAudience() {
        SessionTicketCodec codec = codec(ticketProperties(TICKET_SECRET));
        String ticket = rawTicket(
                TICKET_SECRET,
                TICKET_ISSUER,
                List.of("other-audience"),
                TICKET_TYPE,
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(60),
                true
        );

        assertThatThrownBy(() -> codec.decode(ticket)).isInstanceOf(JwtException.class);
    }

    @Test
    void decode_shouldRejectWrongTicketType() {
        SessionTicketCodec codec = codec(ticketProperties(TICKET_SECRET));
        String ticket = rawTicket(
                TICKET_SECRET,
                TICKET_ISSUER,
                List.of(TICKET_AUDIENCE),
                "access-token",
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(60),
                true
        );

        assertThatThrownBy(() -> codec.decode(ticket))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("type");
    }

    @Test
    void decode_shouldRejectExpiredTicket() {
        SessionTicketCodec codec = codec(ticketProperties(TICKET_SECRET));
        String ticket = rawTicket(
                TICKET_SECRET,
                TICKET_ISSUER,
                List.of(TICKET_AUDIENCE),
                TICKET_TYPE,
                Instant.now().minusSeconds(180),
                Instant.now().minusSeconds(120),
                true
        );

        assertThatThrownBy(() -> codec.decode(ticket)).isInstanceOf(JwtException.class);
    }

    @Test
    void decode_shouldRejectTicketMissingSessionId() {
        SessionTicketCodec codec = codec(ticketProperties(TICKET_SECRET));
        String ticket = rawTicket(
                TICKET_SECRET,
                TICKET_ISSUER,
                List.of(TICKET_AUDIENCE),
                TICKET_TYPE,
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(60),
                false
        );

        assertThatThrownBy(() -> codec.decode(ticket))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("sid");
    }

    @Test
    void constructor_shouldRejectMissingTicketSecret() {
        assertThatThrownBy(() -> codec(ticketProperties("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("im.session-ticket.hmac-secret");
    }

    @Test
    void constructor_shouldRejectShortTicketSecret() {
        assertThatThrownBy(() -> codec(ticketProperties("too-short")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(">= 32 bytes");
    }

    @Test
    void constructor_shouldRejectTicketSecretEqualToNormalizedAccessSecret() {
        assertThatThrownBy(() -> codec(ticketProperties("  " + ACCESS_SECRET + "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must differ from security.jwt.hmac-secret");
    }

    @Test
    void constructor_shouldCountTicketSecretLengthInUtf8Bytes() {
        ImSessionTicketProperties properties = ticketProperties("\u754c".repeat(11));

        assertThatCode(() -> codec(properties)).doesNotThrowAnyException();
    }

    @Test
    void constructor_shouldRejectBlankTicketIssuer() {
        ImSessionTicketProperties properties = ticketProperties(TICKET_SECRET);
        properties.setIssuer("  ");

        assertThatThrownBy(() -> codec(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("im.session-ticket.issuer");
    }

    @Test
    void constructor_shouldRejectBlankTicketAudience() {
        ImSessionTicketProperties properties = ticketProperties(TICKET_SECRET);
        properties.setAudience("  ");

        assertThatThrownBy(() -> codec(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("im.session-ticket.audience");
    }

    private static SessionTicketCodec codec(ImSessionTicketProperties ticketProperties) {
        return new SessionTicketCodec(accessProperties(), ticketProperties);
    }

    private static JwtProperties accessProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setHmacSecret(ACCESS_SECRET);
        properties.setIssuer("community-auth");
        return properties;
    }

    private static ImSessionTicketProperties ticketProperties(String secret) {
        ImSessionTicketProperties properties = new ImSessionTicketProperties();
        properties.setHmacSecret(secret);
        return properties;
    }

    private static NimbusJwtDecoder rawDecoder(String secret) {
        return NimbusJwtDecoder.withSecretKey(secretKey(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private static String rawTicket(
            String secret,
            String issuer,
            List<String> audience,
            String type,
            Instant issuedAt,
            Instant expiresAt,
            boolean includeSessionId
    ) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(audience)
                .subject(USER_ID.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("wid", "worker-a")
                .claim("typ", type);
        if (includeSessionId) {
            claims.claim("sid", "sess-1");
        }
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(secret)));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build()
        )).getTokenValue();
    }

    private static SecretKey secretKey(String secret) {
        return new SecretKeySpec(secret.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
