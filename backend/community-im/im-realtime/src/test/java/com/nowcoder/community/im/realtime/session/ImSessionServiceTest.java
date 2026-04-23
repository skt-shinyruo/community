package com.nowcoder.community.im.realtime.session;

import com.nowcoder.community.im.common.session.OpenImSessionResponse;
import com.nowcoder.community.im.realtime.projection.ProjectionSyncCoordinator;
import com.nowcoder.community.im.realtime.security.JwtVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImSessionServiceTest {

    @Test
    void openSession_shouldCheckProjectionReadinessBeforeBearerValidation() {
        JwtVerifier jwtVerifier = mock(JwtVerifier.class);
        ProjectionSyncCoordinator projectionSyncCoordinator = mock(ProjectionSyncCoordinator.class);
        RendezvousWorkerSelector workerSelector = mock(RendezvousWorkerSelector.class);
        SessionTicketCodec sessionTicketCodec = mock(SessionTicketCodec.class);
        ImSessionProperties properties = properties();
        ResponseStatusException notReady =
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "IM projections are not ready");
        doThrow(notReady).when(projectionSyncCoordinator).requireReady();

        ImSessionService service = new ImSessionService(
                jwtVerifier,
                projectionSyncCoordinator,
                workerSelector,
                sessionTicketCodec,
                properties
        );

        assertThatThrownBy(() -> service.openSession("Bearer some-access-token"))
                .isSameAs(notReady);

        verify(projectionSyncCoordinator).requireReady();
        verifyNoInteractions(jwtVerifier, workerSelector, sessionTicketCodec);
    }

    @Test
    void openSession_shouldMintTicketForSelectedWorkerWhenReady() {
        JwtVerifier jwtVerifier = mock(JwtVerifier.class);
        ProjectionSyncCoordinator projectionSyncCoordinator = mock(ProjectionSyncCoordinator.class);
        RendezvousWorkerSelector workerSelector = mock(RendezvousWorkerSelector.class);
        SessionTicketCodec sessionTicketCodec = mock(SessionTicketCodec.class);
        ImSessionProperties properties = properties();
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000123");

        when(jwtVerifier.verify("some-access-token"))
                .thenReturn(new JwtVerifier.VerifiedJwt(userId, mock(Jwt.class)));
        when(workerSelector.select(userId))
                .thenReturn(new RendezvousWorkerSelector.SelectedWorker("worker-a", "wss://community.example/ws/im/workers/worker-a"));
        when(sessionTicketCodec.encode(anyString(), eq(userId), eq("worker-a"), any()))
                .thenReturn("ticket-1");

        ImSessionService service = new ImSessionService(
                jwtVerifier,
                projectionSyncCoordinator,
                workerSelector,
                sessionTicketCodec,
                properties
        );

        OpenImSessionResponse response = service.openSession("Bearer some-access-token");

        assertThat(response.workerId()).isEqualTo("worker-a");
        assertThat(response.wsUrl()).isEqualTo("wss://community.example/ws/im/workers/worker-a");
        assertThat(response.ticket()).isEqualTo("ticket-1");
        assertThat(response.sessionId()).isNotBlank();
        assertThat(response.expiresAtEpochMillis()).isGreaterThan(System.currentTimeMillis());
        verify(projectionSyncCoordinator).requireReady();
        verify(jwtVerifier).verify("some-access-token");
        verify(workerSelector).select(userId);
        verify(sessionTicketCodec).encode(anyString(), eq(userId), eq("worker-a"), any());
    }

    private static ImSessionProperties properties() {
        ImSessionProperties properties = new ImSessionProperties();
        properties.setTicketTtl(Duration.ofSeconds(90));
        return properties;
    }
}
