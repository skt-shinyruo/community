package com.nowcoder.community.im.ticket;

import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTicketCrossServiceContractTest {

    private static final String SERVICE_SECRET = "service-token-test-secret-distinct-1234567890";
    private static final String TICKET_SECRET = "im-session-ticket-test-secret-distinct-1234567890";

    @Test
    void gatewayIssuedTicketShouldBeAcceptedByIndependentRealtimeCodec() {
        ImSessionTicketProperties gatewayProperties = ticketProperties();
        ImSessionTicketProperties realtimeProperties = ticketProperties();
        SessionTicketCodec gatewayCodec = codec(gatewayProperties);
        SessionTicketCodec realtimeCodec = codec(realtimeProperties);
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000123");
        Instant expiresAt = Instant.now().plusSeconds(120);

        String ticket = gatewayCodec.encode("session-1", userId, "worker-a", expiresAt);

        SessionTicketCodec.TicketClaims claims = realtimeCodec.decode(ticket);
        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.sessionId()).isEqualTo("session-1");
        assertThat(claims.workerId()).isEqualTo("worker-a");
        assertThat(claims.expiresAt()).isEqualTo(expiresAt.truncatedTo(ChronoUnit.SECONDS));
    }

    private static SessionTicketCodec codec(ImSessionTicketProperties ticketProperties) {
        JwtProperties serviceProperties = new JwtProperties();
        serviceProperties.setServiceHmacSecret(SERVICE_SECRET);
        return new SessionTicketCodec(
                ticketProperties,
                ticketProperties.secretKeyOrThrow(serviceProperties)
        );
    }

    private static ImSessionTicketProperties ticketProperties() {
        ImSessionTicketProperties properties = new ImSessionTicketProperties();
        properties.setHmacSecret(TICKET_SECRET);
        properties.setIssuer("community-im-gateway");
        properties.setAudience("im-realtime");
        return properties;
    }
}
