package com.nowcoder.community.im.gateway.ws;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.im.gateway.observability.ImGatewayMetrics;
import com.nowcoder.community.im.gateway.session.ImGatewaySessionProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalImEdgeWebSocketHandlerTest {

    @Test
    void shouldRejectAnOversizedFirstTextFrameBeforeRouting() {
        ConnectTicketRouter router = mock(ConnectTicketRouter.class);
        InternalWorkerBridge bridge = mock(InternalWorkerBridge.class);
        JacksonJsonCodec frameCodec = mock(JacksonJsonCodec.class);
        ImGatewaySessionProperties properties = new ImGatewaySessionProperties();
        properties.getWs().setMaxInboundChars(4);
        WebSocketSession session = sessionWithInbound(Flux.just(textFrame("12345")));
        when(frameCodec.toJson(any())).thenReturn("{\"reasonCode\":\"payload_too_large\"}");

        ExternalImEdgeWebSocketHandler handler = handler(
                router, bridge, frameCodec, properties);

        StepVerifier.create(handler.handle(session))
                .verifyComplete();

        verify(router, never()).route(any());
        verify(session).send(any());
        verify(session).close();
    }

    @Test
    void shouldCloseInsteadOfBufferingAnUnboundedNumberOfFramesBeforeWorkerConsumes() {
        ConnectTicketRouter router = mock(ConnectTicketRouter.class);
        InternalWorkerBridge bridge = mock(InternalWorkerBridge.class);
        JacksonJsonCodec frameCodec = mock(JacksonJsonCodec.class);
        ImGatewaySessionProperties properties = new ImGatewaySessionProperties();
        properties.getWs().setMaxInboundBufferFrames(2);
        Flux<WebSocketMessage> inbound = Flux.concat(
                Flux.just(textFrame("connect")),
                Flux.range(0, 3).map(index -> textFrame("frame-" + index))
        );
        WebSocketSession session = sessionWithInbound(inbound);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(router.route("connect")).thenReturn(new ConnectTicketRouter.RoutingDecision(
                "session-1", "worker-1", URI.create("ws://worker.test/internal/ws/im")));
        when(bridge.bridge(any(), any(), any(), any())).thenReturn(Mono.never());

        ExternalImEdgeWebSocketHandler handler = handler(
                router, bridge, frameCodec, properties, registry);

        StepVerifier.create(handler.handle(session))
                .expectComplete()
                .verify(Duration.ofSeconds(1));

        verify(session).close();
        assertThat(registry.counter(
                "community.im.gateway.bridge.failed",
                "reason",
                "inbound_buffer_overflow"
        ).count()).isEqualTo(1.0);
    }

    private static ExternalImEdgeWebSocketHandler handler(
            ConnectTicketRouter router,
            InternalWorkerBridge bridge,
            JacksonJsonCodec frameCodec,
            ImGatewaySessionProperties properties
    ) {
        return handler(router, bridge, frameCodec, properties, new SimpleMeterRegistry());
    }

    private static ExternalImEdgeWebSocketHandler handler(
            ConnectTicketRouter router,
            InternalWorkerBridge bridge,
            JacksonJsonCodec frameCodec,
            ImGatewaySessionProperties properties,
            SimpleMeterRegistry registry
    ) {
        return new ExternalImEdgeWebSocketHandler(
                router,
                bridge,
                frameCodec,
                properties,
                new ImGatewayMetrics(registry)
        );
    }

    private static WebSocketSession sessionWithInbound(Flux<WebSocketMessage> inbound) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.receive()).thenReturn(inbound);
        when(session.send(any())).thenReturn(Mono.empty());
        when(session.close()).thenReturn(Mono.empty());
        when(session.textMessage(any())).thenAnswer(invocation -> textFrame(invocation.getArgument(0)));
        return session;
    }

    private static WebSocketMessage textFrame(String text) {
        return new WebSocketMessage(
                WebSocketMessage.Type.TEXT,
                DefaultDataBufferFactory.sharedInstance.wrap(text.getBytes(StandardCharsets.UTF_8))
        );
    }
}
