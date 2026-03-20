package com.nowcoder.community.gateway.ws;

import com.nowcoder.community.gateway.shard.ShardRouter;
import com.nowcoder.community.gateway.shard.WorkerDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ExternalImWebSocketHandler implements WebSocketHandler {

    private final WsProxyProperties properties;
    private final AuthFrameParser authFrameParser;
    private final JwtDecoder jwtDecoder;
    private final ShardRouter shardRouter;
    private final InternalWorkerBridgeFactory bridgeFactory;

    @Autowired
    public ExternalImWebSocketHandler(
            WsProxyProperties properties,
            AuthFrameParser authFrameParser,
            JwtDecoder jwtDecoder,
            ShardRouter shardRouter,
            InternalWorkerBridgeFactory bridgeFactory
    ) {
        this.properties = properties;
        this.authFrameParser = authFrameParser;
        this.jwtDecoder = jwtDecoder;
        this.shardRouter = shardRouter;
        this.bridgeFactory = bridgeFactory;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        if (!properties.isAuthRequired()) {
            return resolveDefaultBridge()
                    .map(bridge -> bridge.bridge(session, session.receive().map(WebSocketMessage::getPayloadAsText)))
                    .orElseGet(session::close);
        }
        AtomicReference<ExternalWsSessionState> state = new AtomicReference<>(ExternalWsSessionState.connectedUnauthed());
        Flux<String> inbound = session.receive().map(WebSocketMessage::getPayloadAsText);
        return inbound.switchOnFirst((signal, flux) -> {
            if (!signal.hasValue()) {
                state.set(ExternalWsSessionState.closing(""));
                return session.close();
            }
            String firstFrame = signal.get();
            state.set(ExternalWsSessionState.authValidating());
            AuthFrameParser.ParsedAuthFrame parsed;
            try {
                parsed = authFrameParser.parse(firstFrame);
            } catch (IllegalArgumentException e) {
                state.set(ExternalWsSessionState.closing(""));
                return authError(session, "auth required");
            }

            Jwt jwt;
            try {
                jwt = jwtDecoder.decode(parsed.accessToken());
            } catch (RuntimeException e) {
                state.set(ExternalWsSessionState.closing(""));
                return authError(session, "invalid token");
            }
            String userId = jwt.getSubject();
            Optional<InternalWorkerBridge> maybeBridge = resolveAuthedBridge(userId);
            if (maybeBridge.isEmpty()) {
                state.set(ExternalWsSessionState.closing(userId));
                return error(session, "no worker available");
            }
            state.set(ExternalWsSessionState.authedReady(userId));
            Flux<String> toWorker = Flux.concat(Mono.just(firstFrame), flux.skip(1));
            return maybeBridge.get().bridge(session, toWorker)
                    .doFinally(signalType -> state.set(ExternalWsSessionState.closing(userId)));
        }).then();
    }

    private Optional<InternalWorkerBridge> resolveDefaultBridge() {
        URI defaultWorkerUri = properties.getDefaultWorkerUri();
        if (defaultWorkerUri == null) {
            return Optional.empty();
        }
        return Optional.of(bridgeFactory.create(defaultWorkerUri));
    }

    private Optional<InternalWorkerBridge> resolveAuthedBridge(String userId) {
        Optional<WorkerDescriptor> routed = shardRouter.route(userId);
        if (routed.isPresent()) {
            return routed.map(worker -> bridgeFactory.create(worker.getUri()));
        }
        return resolveDefaultBridge();
    }

    private Mono<Void> authError(WebSocketSession session, String message) {
        return session.send(Mono.just(session.textMessage("{\"type\":\"auth_error\",\"message\":\"" + message + "\"}")))
                .then(session.close());
    }

    private Mono<Void> error(WebSocketSession session, String message) {
        return session.send(Mono.just(session.textMessage("{\"type\":\"error\",\"message\":\"" + message + "\"}")))
                .then(session.close());
    }
}
