package com.nowcoder.community.im.gateway.ws;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
public class InternalWorkerBridgeFactory {

    private static final String HEADER_TRACE_ID = "X-Trace-Id";
    private static final String HEADER_TRACEPARENT = "traceparent";

    private final ReactorNettyWebSocketClient client;

    public InternalWorkerBridgeFactory(
            @Qualifier("imGatewayWebSocketClient") ReactorNettyWebSocketClient client
    ) {
        this.client = client;
    }

    public InternalWorkerBridge create(URI workerUri) {
        return new ReactorNettyInternalWorkerBridge(client, workerUri);
    }

    private record ReactorNettyInternalWorkerBridge(
            ReactorNettyWebSocketClient client,
            URI workerUri
    ) implements InternalWorkerBridge {

        @Override
        public Mono<Void> bridge(WebSocketSession externalSession, Flux<String> outboundFrames, Runnable onOpen) {
            HttpHeaders traceHeaders = buildTraceHeaders(externalSession);
            return client.execute(workerUri, traceHeaders, internal -> {
                if (onOpen != null) {
                    onOpen.run();
                }
                Mono<Void> clientToWorker = internal.send(outboundFrames.map(internal::textMessage));
                Mono<Void> workerToClient = externalSession.send(
                        internal.receive()
                                .handle((message, sink) -> {
                                    if (message.getType() == WebSocketMessage.Type.TEXT) {
                                        sink.next(externalSession.textMessage(message.getPayloadAsText()));
                                        return;
                                    }
                                    sink.error(new UnsupportedWorkerFrameTypeException(message.getType()));
                                })
                                .cast(WebSocketMessage.class)
                );
                return Mono.when(clientToWorker, workerToClient);
            });
        }

        private HttpHeaders buildTraceHeaders(WebSocketSession externalSession) {
            HttpHeaders handshakeHeaders = externalSession.getHandshakeInfo().getHeaders();
            HttpHeaders headers = new HttpHeaders();
            String traceId = handshakeHeaders.getFirst(HEADER_TRACE_ID);
            if (traceId != null && !traceId.isBlank()) {
                headers.set(HEADER_TRACE_ID, traceId.trim());
            }
            String traceparent = handshakeHeaders.getFirst(HEADER_TRACEPARENT);
            if (traceparent != null && !traceparent.isBlank()) {
                headers.set(HEADER_TRACEPARENT, traceparent.trim());
            }
            return headers;
        }
    }

    private static class UnsupportedWorkerFrameTypeException extends RuntimeException {

        UnsupportedWorkerFrameTypeException(WebSocketMessage.Type type) {
            super("unsupported worker websocket frame type: " + type);
        }
    }
}
