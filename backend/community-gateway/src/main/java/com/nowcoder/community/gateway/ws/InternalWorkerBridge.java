package com.nowcoder.community.gateway.ws;

import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface InternalWorkerBridge {

    Mono<Void> bridge(WebSocketSession externalSession, Flux<String> outboundFrames);
}
