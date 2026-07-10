package com.nowcoder.community.im.realtime.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import com.nowcoder.community.im.common.ws.ConnectFrame;
import com.nowcoder.community.im.realtime.presence.RoomLocalIndex;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.presence.RoomLocalPresenceService;
import com.nowcoder.community.im.realtime.presence.RoomPresenceDirectory;
import com.nowcoder.community.im.realtime.projection.MembershipProjectionService;
import com.nowcoder.community.im.realtime.projection.MembershipSnapshotClient;
import com.nowcoder.community.im.realtime.projection.PolicyProjectionService;
import com.nowcoder.community.im.realtime.projection.PolicySnapshotClient;
import com.nowcoder.community.im.realtime.projection.ProjectionSyncCoordinator;
import com.nowcoder.community.im.realtime.service.MessageCommandIngressService;
import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import com.nowcoder.community.im.realtime.session.SessionTicketCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.security.Principal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImWebSocketHandlerContractVersionTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"schemaVersion\":null}",
            "{\"schemaVersion\":0}",
            "{\"schemaVersion\":-1}",
            "{\"schemaVersion\":\"1\"}",
            "{\"schemaVersion\":2}"
    })
    void shouldRejectInvalidSchemaVersionBeforeDispatch(String frameJson) throws Exception {
        Sinks.Many<String> inbound = Sinks.many().unicast().onBackpressureBuffer();
        LinkedBlockingQueue<String> sentMessages = new LinkedBlockingQueue<>();
        WebSocketSession session = session(inbound, sentMessages);
        JwtProperties jwtProperties = jwtProperties();
        ImSessionProperties sessionProperties = sessionProperties();
        ProjectionSyncCoordinator projectionSyncCoordinator = mock(ProjectionSyncCoordinator.class);
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "projection not ready"))
                .when(projectionSyncCoordinator).requireReady();
        MessageCommandIngressService commandIngressService = mock(MessageCommandIngressService.class);

        ImWebSocketHandler handler = new ImWebSocketHandler(
                new ImFrameCodec(new JacksonJsonCodec(JsonMappers.standard())),
                new SessionTicketCodec(jwtProperties, JwtCodecs.jwtDecoder(jwtProperties)),
                sessionProperties,
                projectionSyncCoordinator,
                new MembershipProjectionService(mock(MembershipSnapshotClient.class)),
                new PolicyProjectionService(mock(PolicySnapshotClient.class)),
                commandIngressService,
                new ConnectionRegistry(),
                new RoomLocalPresenceService(
                        new RoomLocalIndex(),
                        mock(RoomPresenceDirectory.class),
                        sessionProperties.getWorkerId()
                ),
                10_000,
                256
        );

        handler.handle(session).subscribe();
        inbound.tryEmitNext(frameJson);
        inbound.tryEmitComplete();

        JsonNode reject = awaitNextFrame(sentMessages);
        assertThat(reject.path("type").asText()).isEqualTo("reject");
        assertThat(reject.path("reasonCode").asText()).isEqualTo("unsupported_schema_version");
        verifyNoInteractions(projectionSyncCoordinator, commandIngressService);
    }

    @Test
    void shouldRejectUnsupportedFutureSchemaVersionAsProtocolError() throws Exception {
        UUID userId = uuid(1);
        UUID toUserId = uuid(2);
        Sinks.Many<String> inbound = Sinks.many().unicast().onBackpressureBuffer();
        LinkedBlockingQueue<String> sentMessages = new LinkedBlockingQueue<>();
        WebSocketSession session = session(inbound, sentMessages);
        JwtProperties jwtProperties = jwtProperties();
        ImSessionProperties sessionProperties = sessionProperties();
        SessionTicketCodec ticketCodec = new SessionTicketCodec(jwtProperties, JwtCodecs.jwtDecoder(jwtProperties));
        MembershipProjectionService membershipProjectionService = new MembershipProjectionService(
                mock(MembershipSnapshotClient.class)
        );
        PolicyProjectionService policyProjectionService = new PolicyProjectionService(mock(PolicySnapshotClient.class));
        policyProjectionService.applyUserMessagingPolicyChanged(policyEvent(userId, true));
        policyProjectionService.applyUserMessagingPolicyChanged(policyEvent(toUserId, true));
        ProjectionSyncCoordinator projectionSyncCoordinator = mock(ProjectionSyncCoordinator.class);
        doNothing().when(projectionSyncCoordinator).requireReady();

        ImWebSocketHandler handler = new ImWebSocketHandler(
                new ImFrameCodec(new JacksonJsonCodec(JsonMappers.standard())),
                ticketCodec,
                sessionProperties,
                projectionSyncCoordinator,
                membershipProjectionService,
                policyProjectionService,
                mock(MessageCommandIngressService.class),
                new ConnectionRegistry(),
                new RoomLocalPresenceService(new RoomLocalIndex(), mock(RoomPresenceDirectory.class), sessionProperties.getWorkerId()),
                10_000,
                256
        );

        String ticket = ticketCodec.encode("sess-1", userId, sessionProperties.getWorkerId(), Instant.now().plusSeconds(120));
        handler.handle(session).subscribe();

        inbound.tryEmitNext(OBJECT_MAPPER.writeValueAsString(new ConnectFrame("connect", ticket)));
        JsonNode connected = awaitFrame(sentMessages, "connected");
        assertThat(connected.path("sessionId").asText()).isEqualTo("sess-1");

        inbound.tryEmitNext("""
                {
                  "type": "sendPrivateText",
                  "schemaVersion": 2,
                  "clientMsgId": "c-future",
                  "toUserId": "%s",
                  "content": "hi from the future"
                }
                """.formatted(toUserId));
        inbound.tryEmitComplete();

        JsonNode reject = awaitFrame(sentMessages, "reject");
        assertThat(reject.path("cmd").asText()).isEqualTo("protocol");
        assertThat(reject.path("reasonCode").asText()).isEqualTo("unsupported_schema_version");
        assertThat(reject.path("message").asText()).contains("unsupported IM schemaVersion 2");
    }

    private static WebSocketSession session(Sinks.Many<String> inbound, LinkedBlockingQueue<String> sentMessages) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("ws-contract-version");
        when(session.getHandshakeInfo()).thenReturn(new HandshakeInfo(
                URI.create("ws://localhost/ws"),
                new HttpHeaders(),
                Mono.<Principal>empty(),
                null
        ));
        when(session.bufferFactory()).thenReturn(new org.springframework.core.io.buffer.DefaultDataBufferFactory());
        when(session.textMessage(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation ->
                new WebSocketMessage(
                        WebSocketMessage.Type.TEXT,
                        session.bufferFactory().wrap(invocation.getArgument(0, String.class).getBytes(StandardCharsets.UTF_8))
                ));
        when(session.receive()).thenReturn(inbound.asFlux()
                .map(text -> new WebSocketMessage(WebSocketMessage.Type.TEXT, session.bufferFactory().wrap(text.getBytes()))));
        when(session.send(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Flux<WebSocketMessage> outbound = invocation.getArgument(0, Flux.class);
            return outbound
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext(sentMessages::offer)
                    .then();
        });
        when(session.close()).thenReturn(Mono.empty());
        return session;
    }

    private static JsonNode awaitFrame(LinkedBlockingQueue<String> sentMessages, String type) throws Exception {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(5).toMillis();
        while (System.currentTimeMillis() < deadline) {
            String message = sentMessages.poll(Duration.ofMillis(100).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (message == null) {
                continue;
            }
            JsonNode node = OBJECT_MAPPER.readTree(message);
            if (type.equals(node.path("type").asText())) {
                return node;
            }
        }
        throw new AssertionError("Timed out waiting for websocket frame type=" + type);
    }

    private static JsonNode awaitNextFrame(LinkedBlockingQueue<String> sentMessages) throws Exception {
        String message = sentMessages.poll(Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        if (message == null) {
            throw new AssertionError("Timed out waiting for websocket frame");
        }
        return OBJECT_MAPPER.readTree(message);
    }

    private static UserMessagingPolicyChanged policyEvent(UUID userId, boolean canSendPrivate) {
        return new UserMessagingPolicyChanged(
                "evt-policy-" + userId,
                userId,
                true,
                false,
                false,
                null,
                null,
                canSendPrivate,
                System.currentTimeMillis(),
                1L
        );
    }

    private static ImSessionProperties sessionProperties() {
        ImSessionProperties properties = new ImSessionProperties();
        properties.setWorkerId("worker-a");
        return properties;
    }

    private static JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("community-test");
        properties.setHmacSecret("ws-contract-version-secret-at-least-32b");
        return properties;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
