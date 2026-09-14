package com.nowcoder.community.im.realtime.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.common.ws.ConnectFrame;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.presence.RoomLocalIndex;
import com.nowcoder.community.im.realtime.presence.RoomLocalPresenceService;
import com.nowcoder.community.im.realtime.presence.RoomPresenceDirectory;
import com.nowcoder.community.im.realtime.projection.MembershipProjectionService;
import com.nowcoder.community.im.realtime.projection.PolicyDecision;
import com.nowcoder.community.im.realtime.projection.PolicyProjectionService;
import com.nowcoder.community.im.realtime.projection.ProjectionSyncCoordinator;
import com.nowcoder.community.im.realtime.service.CommandIngressResult;
import com.nowcoder.community.im.realtime.service.MessageCommandIngressService;
import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import com.nowcoder.community.im.ticket.ImSessionTicketProperties;
import com.nowcoder.community.im.ticket.SessionTicketCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Runtime protocol validation contract: known v1 frames with missing fields,
 * wrong JSON types or wrong schema versions must be rejected before any
 * business processing; valid v1 frames and unknown extension fields keep the
 * current contract behavior.
 */
class ImWebSocketHandlerFrameValidationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TICKET_SECRET = "ws-frame-validation-ticket-secret-distinct-32b";
    private static final String TICKET_ISSUER = "community-im-gateway";
    private static final String TICKET_AUDIENCE = "im-realtime";

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\":\"ping\",\"schemaVersion\":1}",
            "{\"type\":\"ping\",\"schemaVersion\":1,\"sentAtEpochMillis\":null}",
            "{\"type\":\"ping\",\"schemaVersion\":1,\"sentAtEpochMillis\":\"1720000000123\"}",
            "{\"type\":\"ping\",\"schemaVersion\":1,\"sentAtEpochMillis\":1.5}",
            "{\"type\":\"ping\",\"schemaVersion\":1,\"sentAtEpochMillis\":true}"
    })
    void shouldRejectMalformedPingWithoutPong(String pingJson) throws Exception {
        Fixture fixture = newFixture();
        UUID userId = uuid(1);
        connect(fixture, userId);

        fixture.inbound().tryEmitNext(pingJson);

        JsonNode reject = awaitNextFrame(fixture.sentMessages());
        assertThat(reject.path("type").asText()).isEqualTo("reject");
        assertThat(reject.path("cmd").asText()).isEqualTo("ping");
        assertThat(reject.path("code").asInt()).isEqualTo(400);
        assertThat(reject.path("reasonCode").asText()).isEqualTo("invalid_frame");
        assertThat(fixture.sentMessages().poll(250, TimeUnit.MILLISECONDS))
                .as("malformed ping must not produce a pong")
                .isNull();

        // The connection stays usable: a valid ping afterwards is answered.
        fixture.inbound().tryEmitNext("{\"type\":\"ping\",\"schemaVersion\":1,\"sentAtEpochMillis\":1720000000123}");
        JsonNode pong = awaitNextFrame(fixture.sentMessages());
        assertThat(pong.path("type").asText()).isEqualTo("pong");
        assertThat(pong.path("sentAtEpochMillis").asLong()).isEqualTo(1_720_000_000_123L);
    }

    @Test
    void shouldAnswerPongForValidPingWithUnknownExtensionField() throws Exception {
        Fixture fixture = newFixture();
        connect(fixture, uuid(1));

        fixture.inbound().tryEmitNext("""
                {"type":"ping","schemaVersion":1,"sentAtEpochMillis":1720000000456,"nonce":"future-extension"}
                """);

        JsonNode pong = awaitNextFrame(fixture.sentMessages());
        assertThat(pong.path("type").asText()).isEqualTo("pong");
        assertThat(pong.path("sentAtEpochMillis").asLong()).isEqualTo(1_720_000_000_456L);
        assertThat(pong.path("schemaVersion").asInt()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // missing clientMsgId
            "{\"type\":\"sendPrivateText\",\"schemaVersion\":1,\"toUserId\":\"%s\",\"content\":\"hi\"}",
            // numeric clientMsgId
            "{\"type\":\"sendPrivateText\",\"schemaVersion\":1,\"clientMsgId\":123,\"toUserId\":\"%s\",\"content\":\"hi\"}",
            // numeric toUserId
            "{\"type\":\"sendPrivateText\",\"schemaVersion\":1,\"clientMsgId\":\"c-1\",\"toUserId\":123,\"content\":\"hi\"}",
            // non-UUID toUserId
            "{\"type\":\"sendPrivateText\",\"schemaVersion\":1,\"clientMsgId\":\"c-1\",\"toUserId\":\"not-a-uuid\",\"content\":\"hi\"}",
            // missing content
            "{\"type\":\"sendPrivateText\",\"schemaVersion\":1,\"clientMsgId\":\"c-1\",\"toUserId\":\"%s\"}",
            // numeric content
            "{\"type\":\"sendPrivateText\",\"schemaVersion\":1,\"clientMsgId\":\"c-1\",\"toUserId\":\"%s\",\"content\":123}"
    })
    void shouldRejectMalformedSendPrivateTextBeforeBusinessChecks(String frameJson) throws Exception {
        Fixture fixture = newFixture();
        UUID userId = uuid(1);
        UUID toUserId = uuid(2);
        connect(fixture, userId);

        fixture.inbound().tryEmitNext(frameJson.formatted(toUserId));

        JsonNode reject = awaitNextFrame(fixture.sentMessages());
        assertThat(reject.path("type").asText()).isEqualTo("reject");
        assertThat(reject.path("cmd").asText()).isEqualTo("sendPrivateText");
        assertThat(reject.path("code").asInt()).isEqualTo(400);
        assertThat(reject.path("reasonCode").asText()).isEqualTo("invalid_frame");
        verifyNoInteractions(
                fixture.projectionSyncCoordinator(),
                fixture.policyProjectionService(),
                fixture.membershipProjectionService(),
                fixture.commandIngressService()
        );
    }

    @Test
    void shouldEchoTextualClientMsgIdWhenSendFrameValidationFails() throws Exception {
        Fixture fixture = newFixture();
        UUID userId = uuid(1);
        connect(fixture, userId);

        // Missing content, but a well-formed clientMsgId the sender can correlate.
        fixture.inbound().tryEmitNext("""
                {"type":"sendPrivateText","schemaVersion":1,"clientMsgId":"c-1","toUserId":"%s"}
                """.formatted(uuid(2)));
        JsonNode reject = awaitNextFrame(fixture.sentMessages());
        assertThat(reject.path("reasonCode").asText()).isEqualTo("invalid_frame");
        assertThat(reject.path("clientMsgId").asText()).isEqualTo("c-1");

        // A non-textual clientMsgId is not coerced into the reject echo.
        fixture.inbound().tryEmitNext("""
                {"type":"sendPrivateText","schemaVersion":1,"clientMsgId":123,"toUserId":"%s","content":"hi"}
                """.formatted(uuid(2)));
        JsonNode numericReject = awaitNextFrame(fixture.sentMessages());
        assertThat(numericReject.path("reasonCode").asText()).isEqualTo("invalid_frame");
        assertThat(numericReject.path("clientMsgId").asText()).isEqualTo("");
    }

    @Test
    void shouldPassValidSendPrivateTextToCommandIngress() throws Exception {
        Fixture fixture = newFixture();
        UUID userId = uuid(1);
        UUID toUserId = uuid(2);
        connect(fixture, userId);
        when(fixture.policyProjectionService().canSendPrivate(userId, toUserId))
                .thenReturn(PolicyDecision.allow());
        when(fixture.commandIngressService().sendPrivate(any(), any(), any(), any()))
                .thenReturn(Mono.just(CommandIngressResult.acked("sendPrivateText", "c-ok", "req-1")));

        fixture.inbound().tryEmitNext("""
                {"type":"sendPrivateText","schemaVersion":1,"clientMsgId":"c-ok","toUserId":"%s","content":"hello","futureFlag":true}
                """.formatted(toUserId));

        verify(fixture.commandIngressService(), org.mockito.Mockito.timeout(5_000))
                .sendPrivate(any(), eq(toUserId), eq("c-ok"), eq("hello"));
        JsonNode ack = awaitNextFrame(fixture.sentMessages());
        assertThat(ack.path("type").asText()).isEqualTo("ack");
        assertThat(ack.path("cmd").asText()).isEqualTo("sendPrivateText");
        assertThat(ack.path("clientMsgId").asText()).isEqualTo("c-ok");
        assertThat(ack.path("requestId").asText()).isEqualTo("req-1");
    }

    @Test
    void shouldSendRejectFrameWhenCommandIngressReportsFailure() throws Exception {
        Fixture fixture = newFixture();
        UUID userId = uuid(1);
        UUID toUserId = uuid(2);
        connect(fixture, userId);
        when(fixture.policyProjectionService().canSendPrivate(userId, toUserId))
                .thenReturn(PolicyDecision.allow());
        when(fixture.commandIngressService().sendPrivate(any(), any(), any(), any()))
                .thenReturn(Mono.just(CommandIngressResult.rejected(
                        "sendPrivateText", "c-fail", "req-9", 503, "kafka_send_failed", "kafka send failed")));

        fixture.inbound().tryEmitNext("""
                {"type":"sendPrivateText","schemaVersion":1,"clientMsgId":"c-fail","toUserId":"%s","content":"hello"}
                """.formatted(toUserId));

        verify(fixture.commandIngressService(), org.mockito.Mockito.timeout(5_000))
                .sendPrivate(any(), eq(toUserId), eq("c-fail"), eq("hello"));
        JsonNode reject = awaitNextFrame(fixture.sentMessages());
        assertThat(reject.path("type").asText()).isEqualTo("reject");
        assertThat(reject.path("cmd").asText()).isEqualTo("sendPrivateText");
        assertThat(reject.path("clientMsgId").asText()).isEqualTo("c-fail");
        assertThat(reject.path("requestId").asText()).isEqualTo("req-9");
        assertThat(reject.path("code").asInt()).isEqualTo(503);
        assertThat(reject.path("reasonCode").asText()).isEqualTo("kafka_send_failed");
    }

    @Test
    void shouldRejectMalformedSendRoomTextBeforeBusinessChecks() throws Exception {
        Fixture fixture = newFixture();
        UUID userId = uuid(1);
        connect(fixture, userId);

        fixture.inbound().tryEmitNext("""
                {"type":"sendRoomText","schemaVersion":1,"clientMsgId":"c-room","content":"hi"}
                """);

        JsonNode reject = awaitNextFrame(fixture.sentMessages());
        assertThat(reject.path("type").asText()).isEqualTo("reject");
        assertThat(reject.path("cmd").asText()).isEqualTo("sendRoomText");
        assertThat(reject.path("code").asInt()).isEqualTo(400);
        assertThat(reject.path("reasonCode").asText()).isEqualTo("invalid_frame");
        assertThat(reject.path("clientMsgId").asText()).isEqualTo("c-room");
        verifyNoInteractions(
                fixture.projectionSyncCoordinator(),
                fixture.policyProjectionService(),
                fixture.membershipProjectionService(),
                fixture.commandIngressService()
        );
    }

    @Test
    void shouldPassValidSendRoomTextToCommandIngress() throws Exception {
        Fixture fixture = newFixture();
        UUID userId = uuid(1);
        UUID roomId = uuid(9);
        connect(fixture, userId);
        when(fixture.membershipProjectionService().isMember(roomId, userId)).thenReturn(true);
        when(fixture.commandIngressService().sendRoom(any(), any(), any(), any()))
                .thenReturn(Mono.just(CommandIngressResult.acked("sendRoomText", "c-room", "req-2")));

        fixture.inbound().tryEmitNext("""
                {"type":"sendRoomText","schemaVersion":1,"clientMsgId":"c-room","roomId":"%s","content":"room hello"}
                """.formatted(roomId));

        verify(fixture.commandIngressService(), org.mockito.Mockito.timeout(5_000))
                .sendRoom(any(), eq(roomId), eq("c-room"), eq("room hello"));
        JsonNode ack = awaitNextFrame(fixture.sentMessages());
        assertThat(ack.path("type").asText()).isEqualTo("ack");
        assertThat(ack.path("cmd").asText()).isEqualTo("sendRoomText");
        assertThat(ack.path("clientMsgId").asText()).isEqualTo("c-room");
        assertThat(ack.path("requestId").asText()).isEqualTo("req-2");
    }

    @Test
    void shouldRejectConnectFrameWithMissingTicket() throws Exception {
        Fixture fixture = newFixture();

        fixture.inbound().tryEmitNext("{\"type\":\"connect\",\"schemaVersion\":1}");

        JsonNode reject = awaitNextFrame(fixture.sentMessages());
        assertThat(reject.path("type").asText()).isEqualTo("reject");
        assertThat(reject.path("cmd").asText()).isEqualTo("connect");
        assertThat(reject.path("code").asInt()).isEqualTo(400);
        assertThat(reject.path("reasonCode").asText()).isEqualTo("invalid_frame");
        assertThat(fixture.connectionRegistry().listByUserId(uuid(1))).isEmpty();
        verifyNoInteractions(
                fixture.membershipProjectionService(),
                fixture.policyProjectionService(),
                fixture.commandIngressService()
        );
    }

    private static Fixture newFixture() {
        Sinks.Many<String> inbound = Sinks.many().unicast().onBackpressureBuffer();
        LinkedBlockingQueue<String> sentMessages = new LinkedBlockingQueue<>();
        WebSocketSession session = session(inbound, sentMessages);
        JwtProperties jwtProperties = jwtProperties();
        ImSessionProperties sessionProperties = sessionProperties();
        SessionTicketCodec ticketCodec = sessionTicketCodec(jwtProperties);
        ProjectionSyncCoordinator projectionSyncCoordinator = mock(ProjectionSyncCoordinator.class);
        MembershipProjectionService membershipProjectionService = mock(MembershipProjectionService.class);
        PolicyProjectionService policyProjectionService = mock(PolicyProjectionService.class);
        MessageCommandIngressService commandIngressService = mock(MessageCommandIngressService.class);
        ConnectionRegistry connectionRegistry = new ConnectionRegistry();
        ImWebSocketHandler handler = new ImWebSocketHandler(
                new ImFrameCodec(new JacksonJsonCodec(JacksonJsonCodec.standardMapper())),
                ticketCodec,
                sessionProperties,
                projectionSyncCoordinator,
                membershipProjectionService,
                policyProjectionService,
                commandIngressService,
                connectionRegistry,
                new RoomLocalPresenceService(
                        new RoomLocalIndex(),
                        mock(RoomPresenceDirectory.class),
                        sessionProperties.getWorkerId()
                ),
                10_000,
                256
        );
        handler.handle(session).subscribe();
        return new Fixture(
                inbound,
                sentMessages,
                ticketCodec,
                sessionProperties,
                projectionSyncCoordinator,
                membershipProjectionService,
                policyProjectionService,
                commandIngressService,
                connectionRegistry
        );
    }

    private static void connect(Fixture fixture, UUID userId) throws Exception {
        String ticket = fixture.ticketCodec().encode(
                "sess-" + userId,
                userId,
                fixture.sessionProperties().getWorkerId(),
                Instant.now().plusSeconds(120)
        );
        fixture.inbound().tryEmitNext(OBJECT_MAPPER.writeValueAsString(new ConnectFrame("connect", ticket)));
        JsonNode connected = awaitNextFrame(fixture.sentMessages());
        assertThat(connected.path("type").asText()).isEqualTo("connected");
        clearInvocations(
                fixture.projectionSyncCoordinator(),
                fixture.membershipProjectionService(),
                fixture.policyProjectionService(),
                fixture.commandIngressService()
        );
    }

    private record Fixture(
            Sinks.Many<String> inbound,
            LinkedBlockingQueue<String> sentMessages,
            SessionTicketCodec ticketCodec,
            ImSessionProperties sessionProperties,
            ProjectionSyncCoordinator projectionSyncCoordinator,
            MembershipProjectionService membershipProjectionService,
            PolicyProjectionService policyProjectionService,
            MessageCommandIngressService commandIngressService,
            ConnectionRegistry connectionRegistry
    ) {
    }

    private static WebSocketSession session(Sinks.Many<String> inbound, LinkedBlockingQueue<String> sentMessages) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("ws-frame-validation");
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

    private static JsonNode awaitNextFrame(LinkedBlockingQueue<String> sentMessages) throws Exception {
        String message = sentMessages.poll(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
        if (message == null) {
            throw new AssertionError("Timed out waiting for websocket frame");
        }
        return OBJECT_MAPPER.readTree(message);
    }

    private static ImSessionProperties sessionProperties() {
        ImSessionProperties properties = new ImSessionProperties();
        properties.setWorkerId("worker-a");
        return properties;
    }

    private static JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("community-test");
        properties.setServiceHmacSecret("ws-frame-validation-service-secret-at-least-32b");
        return properties;
    }

    private static SessionTicketCodec sessionTicketCodec(JwtProperties serviceProperties) {
        ImSessionTicketProperties ticketProperties = new ImSessionTicketProperties();
        ticketProperties.setHmacSecret(TICKET_SECRET);
        ticketProperties.setIssuer(TICKET_ISSUER);
        ticketProperties.setAudience(TICKET_AUDIENCE);
        return new SessionTicketCodec(
                ticketProperties,
                ticketProperties.secretKeyOrThrow(serviceProperties.getServiceHmacSecret())
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
