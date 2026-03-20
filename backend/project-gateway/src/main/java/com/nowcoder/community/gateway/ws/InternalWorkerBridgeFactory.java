package com.nowcoder.community.gateway.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
public class InternalWorkerBridgeFactory {

    private final ReactorNettyWebSocketClient client;

    public InternalWorkerBridgeFactory(ReactorNettyWebSocketClient client) {
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
            return client.execute(workerUri, internal -> {
                Mono<Void> clientToWorker = internal.send(outboundFrames.map(internal::textMessage));
                Mono<Void> workerToClient = externalSession.send(
                        internal.receive().map(message -> externalSession.textMessage(message.getPayloadAsText()))
                );
                return Mono.when(clientToWorker, workerToClient);
            });
        }
    }
}
