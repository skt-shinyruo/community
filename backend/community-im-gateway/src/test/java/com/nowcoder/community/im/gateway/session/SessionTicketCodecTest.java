package com.nowcoder.community.im.gateway.session;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

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
    void encode_shouldProduceCanonicalRawClaimTypesAtDecoderConversionBoundary() {
        SessionTicketCodec codec = codec(ticketProperties(TICKET_SECRET));
        String ticket = codec.encode(
                "sess-1",
                USER_ID,
                "worker-a",
                Instant.now().plusSeconds(90)
        );
        AtomicReference<Map<String, Object>> observedClaims = new AtomicReference<>();
        MappedJwtClaimSetConverter defaults = MappedJwtClaimSetConverter.withDefaults(Map.of());
        NimbusJwtDecoder decoder = rawDecoder(TICKET_SECRET);
        decoder.setClaimSetConverter(claims -> {
            observedClaims.set(Map.copyOf(claims));
            return defaults.convert(claims);
        });

        decoder.decode(ticket);

        assertThat(observedClaims.get()).containsOnlyKeys("iss", "aud", "typ", "sub", "sid", "wid", "iat", "exp");
        assertThat(observedClaims.get())
                .hasEntrySatisfying("iss", value -> assertThat(value).isInstanceOf(String.class))
                .hasEntrySatisfying("typ", value -> assertThat(value).isInstanceOf(String.class))
                .hasEntrySatisfying("sub", value -> assertThat(value).isInstanceOf(String.class))
                .hasEntrySatisfying("sid", value -> assertThat(value).isInstanceOf(String.class))
                .hasEntrySatisfying("wid", value -> assertThat(value).isInstanceOf(String.class))
                .hasEntrySatisfying("iat", value -> assertThat(value).isInstanceOf(Date.class))
                .hasEntrySatisfying("exp", value -> assertThat(value).isInstanceOf(Date.class));
        Object rawAudience = observedClaims.get().get("aud");
        assertThat(rawAudience).isInstanceOf(List.class);
        assertThat((List<?>) rawAudience).hasSize(1);
        assertThat(((List<?>) rawAudience).get(0)).isEqualTo(TICKET_AUDIENCE);
    }

    @ParameterizedTest(name = "missing raw claim {0}")
    @MethodSource("requiredClaimNames")
    void decode_shouldRejectMissingRequiredRawClaim(String claimName) throws JOSEException {
        Map<String, Object> claims = canonicalRawClaims();
        claims.remove(claimName);

        String ticket = signedRawTicket(TICKET_SECRET, claims);

        assertThatThrownBy(() -> codec(ticketProperties(TICKET_SECRET)).decode(ticket))
                .isInstanceOf(JwtException.class);
    }

    @ParameterizedTest(name = "wrong raw type for {0}")
    @MethodSource("wrongTypeClaims")
    void decode_shouldRejectWrongTypeRequiredRawClaim(String claimName, Object wrongValue) throws JOSEException {
        Map<String, Object> claims = canonicalRawClaims();
        claims.put(claimName, wrongValue);

        String ticket = signedRawTicket(TICKET_SECRET, claims);

        assertThatThrownBy(() -> codec(ticketProperties(TICKET_SECRET)).decode(ticket))
                .isInstanceOf(JwtException.class);
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
    void decode_shouldRejectNonUuidSubjectWithoutExposingSubjectValue() throws JOSEException {
        String invalidSubject = "non-uuid-subject-sentinel";
        Map<String, Object> claims = canonicalRawClaims();
        claims.put("sub", invalidSubject);

        String ticket = signedRawTicket(TICKET_SECRET, claims);

        assertThatThrownBy(() -> codec(ticketProperties(TICKET_SECRET)).decode(ticket))
                .isInstanceOf(BadJwtException.class)
                .hasMessage("invalid IM session ticket subject")
                .hasNoCause();
    }

    @Test
    void properties_shouldRejectMissingTicketSecret() {
        assertThatThrownBy(() -> ticketProperties(null).secretKeyOrThrow(accessProperties()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("im.session-ticket.hmac-secret");
    }

    @Test
    void properties_shouldRejectBlankTicketSecret() {
        assertThatThrownBy(() -> ticketProperties("  ").secretKeyOrThrow(accessProperties()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("im.session-ticket.hmac-secret");
    }

    @Test
    void properties_shouldRejectShortTicketSecret() {
        assertThatThrownBy(() -> ticketProperties("too-short").secretKeyOrThrow(accessProperties()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(">= 32 bytes");
    }

    @Test
    void properties_shouldRejectNormalizedTicketSecretEqualToTrimmedAccessSecret() {
        JwtProperties accessProperties = accessProperties();
        accessProperties.setHmacSecret("  " + ACCESS_SECRET + "  ");

        assertThatThrownBy(() -> ticketProperties("\t" + ACCESS_SECRET + "\n")
                .secretKeyOrThrow(accessProperties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must differ from security.jwt.hmac-secret");
    }

    @Test
    void properties_shouldCountTicketSecretLengthInUtf8Bytes() {
        ImSessionTicketProperties properties = ticketProperties("\u754c".repeat(11));

        assertThatCode(() -> properties.secretKeyOrThrow(accessProperties())).doesNotThrowAnyException();
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
        return new SessionTicketCodec(
                ticketProperties,
                ticketProperties.secretKeyOrThrow(accessProperties())
        );
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

    private static Stream<String> requiredClaimNames() {
        return Stream.of("iss", "aud", "typ", "sub", "sid", "wid", "iat", "exp");
    }

    private static Stream<Arguments> wrongTypeClaims() {
        return Stream.of(
                Arguments.of("iss", 42L),
                Arguments.of("aud", List.of(42L)),
                Arguments.of("typ", true),
                Arguments.of("sub", true),
                Arguments.of("sid", 42L),
                Arguments.of("wid", true),
                Arguments.of("iat", "not-a-numeric-date"),
                Arguments.of("exp", "not-a-numeric-date")
        );
    }

    private static Map<String, Object> canonicalRawClaims() {
        Instant issuedAt = Instant.now().minusSeconds(1);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", TICKET_ISSUER);
        claims.put("aud", List.of(TICKET_AUDIENCE));
        claims.put("typ", TICKET_TYPE);
        claims.put("sub", USER_ID.toString());
        claims.put("sid", "sess-1");
        claims.put("wid", "worker-a");
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("exp", issuedAt.plusSeconds(60).getEpochSecond());
        return claims;
    }

    private static String signedRawTicket(String secret, Map<String, Object> claims) throws JOSEException {
        JWSObject jws = new JWSObject(
                new JWSHeader(JWSAlgorithm.HS256),
                new Payload(claims)
        );
        jws.sign(new MACSigner(secretKey(secret)));
        return jws.serialize();
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
