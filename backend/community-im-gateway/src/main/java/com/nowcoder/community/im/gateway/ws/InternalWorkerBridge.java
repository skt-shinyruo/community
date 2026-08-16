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
public class InternalWorkerBridge {

    private static final String HEADER_TRACEPARENT = "traceparent";

    private final ReactorNettyWebSocketClient client;

    public InternalWorkerBridge(@Qualifier("imGatewayWebSocketClient") ReactorNettyWebSocketClient client) {
        this.client = client;
    }

    public Mono<Void> bridge(
            URI workerUri,
            WebSocketSession externalSession,
            Flux<String> outboundFrames,
            Runnable onOpen
    ) {
        return client.execute(workerUri, traceHeaders(externalSession), internal -> {
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

    private static HttpHeaders traceHeaders(WebSocketSession session) {
        String traceparent = session.getHandshakeInfo().getHeaders().getFirst(HEADER_TRACEPARENT);
        HttpHeaders headers = new HttpHeaders();
        if (traceparent != null && !traceparent.isBlank()) {
            headers.set(HEADER_TRACEPARENT, traceparent.trim());
        }
        return headers;
    }

    private static class UnsupportedWorkerFrameTypeException extends RuntimeException {

        UnsupportedWorkerFrameTypeException(WebSocketMessage.Type type) {
            super("unsupported worker websocket frame type: " + type);
        }
    }
}
