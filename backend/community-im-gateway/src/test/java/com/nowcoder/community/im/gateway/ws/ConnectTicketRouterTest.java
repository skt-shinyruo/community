package com.nowcoder.community.im.gateway.ws;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.common.ws.ConnectFrame;
import com.nowcoder.community.im.gateway.session.ImSessionTicketProperties;
import com.nowcoder.community.im.gateway.session.SessionTicketCodec;
import com.nowcoder.community.im.gateway.shard.WorkerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectTicketRouterTest {

    private static final String ACCESS_SECRET = "access-token-test-secret-distinct-1234567890";
    private static final String TICKET_SECRET = "im-session-ticket-test-secret-distinct-1234567890";
    private static final String TICKET_ISSUER = "community-im-gateway";
    private static final String TICKET_AUDIENCE = "im-realtime";
    private static final String TICKET_TYPE = "im-session-ticket";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000123");
    private static final Instant EXPIRED_TICKET_EXPIRATION = Instant.parse("2024-01-02T03:04:05Z");

    @Test
    void route_shouldNotRetainExpiredTicketValidationCause() {
        ImGatewayFrameCodec frameCodec = new ImGatewayFrameCodec(new JacksonJsonCodec(JsonMappers.standard()));
        SessionTicketCodec ticketCodec = ticketCodec();
        ConnectTicketRouter router = new ConnectTicketRouter(
                frameCodec,
                ticketCodec,
                new WorkerRegistry(List.of())
        );
        String ticket = expiredTicket();
        String connectFrame = frameCodec.write(new ConnectFrame("connect", ticket));

        assertThatThrownBy(() -> router.route(connectFrame))
                .isExactlyInstanceOf(ConnectTicketRouter.RoutingException.class)
                .hasMessage("invalid ticket")
                .hasNoCause();
    }

    private static SessionTicketCodec ticketCodec() {
        JwtProperties accessProperties = new JwtProperties();
        accessProperties.setHmacSecret(ACCESS_SECRET);
        ImSessionTicketProperties ticketProperties = new ImSessionTicketProperties();
        ticketProperties.setHmacSecret(TICKET_SECRET);
        ticketProperties.setIssuer(TICKET_ISSUER);
        ticketProperties.setAudience(TICKET_AUDIENCE);
        return new SessionTicketCodec(ticketProperties, ticketProperties.secretKeyOrThrow(accessProperties));
    }

    private static String expiredTicket() {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(TICKET_ISSUER)
                .audience(List.of(TICKET_AUDIENCE))
                .subject(USER_ID.toString())
                .issuedAt(EXPIRED_TICKET_EXPIRATION.minusSeconds(60))
                .expiresAt(EXPIRED_TICKET_EXPIRATION)
                .claim("sid", "sess-1")
                .claim("wid", "worker-a")
                .claim("typ", TICKET_TYPE)
                .build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(ticketSecretKey()));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims
        )).getTokenValue();
    }

    private static SecretKey ticketSecretKey() {
        return new SecretKeySpec(TICKET_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
