package com.nowcoder.community.im.realtime.session;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionTicketCodecTest {

    @Test
    void encodeAndDecode_shouldRoundTripTicketClaims() {
        JwtProperties properties = jwtProperties();
        SessionTicketCodec codec = new SessionTicketCodec(properties, JwtCodecs.jwtDecoder(properties));
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000123");
        Instant expiresAt = Instant.now().plusSeconds(90).truncatedTo(ChronoUnit.SECONDS);

        String ticket = codec.encode("sess-1", userId, "worker-a", expiresAt);
        SessionTicketCodec.TicketClaims claims = codec.decode(ticket);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.sessionId()).isEqualTo("sess-1");
        assertThat(claims.workerId()).isEqualTo("worker-a");
        assertThat(claims.issuedAt()).isNotNull();
        assertThat(claims.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void decode_shouldRejectAccessTokenWithoutSessionTicketType() {
        JwtProperties properties = jwtProperties();
        SessionTicketCodec codec = new SessionTicketCodec(properties, JwtCodecs.jwtDecoder(properties));
        JwtEncoder encoder = JwtCodecs.jwtEncoder(properties);
        String accessToken = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer(JwtCodecs.resolvedIssuer(properties))
                        .subject("00000000-0000-7000-8000-000000000123")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build()
        )).getTokenValue();

        assertThatThrownBy(() -> codec.decode(accessToken))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("type");
    }

    @Test
    void decode_shouldRejectTicketMissingSessionId() {
        JwtProperties properties = jwtProperties();
        SessionTicketCodec codec = new SessionTicketCodec(properties, JwtCodecs.jwtDecoder(properties));
        JwtEncoder encoder = JwtCodecs.jwtEncoder(properties);
        String malformedTicket = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer(JwtCodecs.resolvedIssuer(properties))
                        .subject("00000000-0000-7000-8000-000000000123")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60))
                        .claim("typ", "im-session-ticket")
                        .claim("wid", "worker-a")
                        .build()
        )).getTokenValue();

        assertThatThrownBy(() -> codec.decode(malformedTicket))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("sid");
    }

    private static JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setHmacSecret("im-realtime-session-ticket-secret-1234567890");
        properties.setIssuer("community-auth");
        return properties;
    }
}
