package com.nowcoder.community.gateway.ws;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
public class InternalWorkerBridgeFactory {

    private static final String HEADER_TRACEPARENT = "traceparent";

    private final ReactorNettyWebSocketClient client;

    public InternalWorkerBridgeFactory(@Qualifier("gatewayWebSocketClient") ReactorNettyWebSocketClient client) {
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
        public Mono<Void> bridge(WebSocketSession externalSession, Flux<String> outboundFrames) {
            HttpHeaders traceHeaders = buildTraceHeaders(externalSession);
            return client.execute(workerUri, traceHeaders, internal -> {
                Mono<Void> clientToWorker = internal.send(outboundFrames.map(internal::textMessage));
                Mono<Void> workerToClient = externalSession.send(
                        internal.receive().map(message -> externalSession.textMessage(message.getPayloadAsText()))
                );
                return Mono.when(clientToWorker, workerToClient);
            });
        }

        private HttpHeaders buildTraceHeaders(WebSocketSession externalSession) {
            HttpHeaders handshakeHeaders = externalSession.getHandshakeInfo().getHeaders();
            HttpHeaders headers = new HttpHeaders();
            String traceparent = handshakeHeaders.getFirst(HEADER_TRACEPARENT);
            if (traceparent != null && !traceparent.isBlank()) {
                headers.set(HEADER_TRACEPARENT, traceparent.trim());
            }
            return headers;
        }
    }
}
