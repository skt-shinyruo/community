package com.nowcoder.community.gateway.ws;

import com.nowcoder.community.gateway.shard.WorkerDescriptor;
import com.nowcoder.community.gateway.shard.WorkerRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
public class ExternalImWebSocketHandler implements WebSocketHandler {

    private final WorkerPathResolver workerPathResolver;
    private final WorkerRegistry workerRegistry;
    private final InternalWorkerBridgeFactory bridgeFactory;

    public ExternalImWebSocketHandler(
            WorkerPathResolver workerPathResolver,
            WorkerRegistry workerRegistry,
            InternalWorkerBridgeFactory bridgeFactory
    ) {
        this.workerPathResolver = workerPathResolver;
        this.workerRegistry = workerRegistry;
        this.bridgeFactory = bridgeFactory;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        try {
            String workerId = workerPathResolver.resolve(session.getHandshakeInfo().getUri());
            WorkerDescriptor worker = workerRegistry.healthyWorkers().stream()
                    .filter(candidate -> workerId.equals(candidate.getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("worker not found: " + workerId));
            return bridgeFactory.create(worker.getUri())
                    .bridge(session, session.receive().map(WebSocketMessage::getPayloadAsText));
        } catch (IllegalArgumentException e) {
            return error(session, e.getMessage());
        }
    }

    private Mono<Void> error(WebSocketSession session, String message) {
        return session.send(Mono.just(session.textMessage("{\"type\":\"error\",\"message\":\"" + message + "\"}")))
                .then(session.close());
    }
}
