package com.nowcoder.community.im.gateway.ws;

import com.nowcoder.community.im.common.ws.RejectFrame;
import com.nowcoder.community.im.gateway.session.ImGatewaySessionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeoutException;

@Component
public class ExternalImEdgeWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ExternalImEdgeWebSocketHandler.class);
    private static final String HEADER_TRACE_ID = "X-Trace-Id";

    private final ConnectTicketRouter connectTicketRouter;
    private final InternalWorkerBridgeFactory bridgeFactory;
    private final ImGatewayFrameCodec frameCodec;
    private final ImGatewaySessionProperties properties;

    public ExternalImEdgeWebSocketHandler(
            ConnectTicketRouter connectTicketRouter,
            InternalWorkerBridgeFactory bridgeFactory,
            ImGatewayFrameCodec frameCodec,
            ImGatewaySessionProperties properties
    ) {
        this.connectTicketRouter = connectTicketRouter;
        this.bridgeFactory = bridgeFactory;
        this.frameCodec = frameCodec;
        this.properties = properties;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        AtomicBoolean firstSeen = new AtomicBoolean(false);
        Sinks.One<InboundFrame> firstMessage = Sinks.one();
        Sinks.Many<InboundFrame> subsequentFrames = Sinks.many().unicast().onBackpressureBuffer();

        Mono<Void> receivePump = session.receive()
                .doOnNext(message -> routeInboundMessage(firstSeen, firstMessage, subsequentFrames, message))
                .doOnError(ex -> failInbound(firstSeen, firstMessage, subsequentFrames, ex))
                .doOnComplete(() -> completeInbound(firstSeen, firstMessage, subsequentFrames))
                .then();
        Mono<Void> route = firstMessage.asMono()
                .timeout(firstFrameTimeout())
                .flatMap(message -> handleFirstMessage(session, message, subsequentFrames.asFlux()))
                .switchIfEmpty(rejectAndClose(session, 401, "connect_required", "connect required"))
                .onErrorResume(TimeoutException.class,
                        ex -> rejectAndClose(session, 408, "connect_timeout", "connect timeout"))
                .onErrorResume(ex -> session.close());

        return Mono.when(receivePump, route).then();
    }

    private Mono<Void> handleFirstMessage(
            WebSocketSession session,
            InboundFrame firstMessage,
            Flux<InboundFrame> subsequentFrames
    ) {
        if (firstMessage.type() != WebSocketMessage.Type.TEXT) {
            return rejectAndClose(session, 400, "unsupported_frame_type", "unsupported frame type");
        }
        String firstFrame = firstMessage.text();
        ConnectTicketRouter.RoutingDecision decision;
        try {
            decision = connectTicketRouter.route(firstFrame);
        } catch (ConnectTicketRouter.RoutingException ex) {
            return rejectAndClose(session, ex.code(), ex.reasonCode(), ex.getMessage());
        } catch (RuntimeException ex) {
            return rejectAndClose(session, 401, "invalid_ticket", "invalid ticket");
        }

        Flux<String> outbound = Flux.concat(Mono.just(firstFrame), subsequentTextFrames(session, subsequentFrames));
        return bridgeFactory.create(decision.workerUri())
                .bridge(session, outbound)
                .onErrorResume(ex -> {
                    logBridgeFailure(session, decision, ex);
                    return session.close();
                });
    }

    private Mono<Void> rejectAndClose(WebSocketSession session, int code, String reasonCode, String message) {
        RejectFrame reject = new RejectFrame("reject", "connect", "", "", code, reasonCode, message);
        return session.send(Mono.just(session.textMessage(frameCodec.write(reject))))
                .then(session.close());
    }

    private Flux<String> subsequentTextFrames(WebSocketSession session, Flux<InboundFrame> frames) {
        return frames.handle((message, sink) -> {
            if (message.type() == WebSocketMessage.Type.TEXT) {
                sink.next(message.text());
                return;
            }
            sink.error(new UnsupportedFrameTypeException(message.type()));
        }).cast(String.class);
    }

    private Duration firstFrameTimeout() {
        long timeoutMs = properties.getWs().getFirstFrameTimeoutMs();
        return Duration.ofMillis(timeoutMs);
    }

    private void logBridgeFailure(
            WebSocketSession session,
            ConnectTicketRouter.RoutingDecision decision,
            Throwable ex
    ) {
        log.warn("IM websocket bridge failed: workerId={}, sessionId={}, traceId={}, reason={}, errorType={}",
                decision.workerId(), decision.sessionId(), traceId(session), safeReason(ex), ex.getClass().getName());
    }

    private static String traceId(WebSocketSession session) {
        if (session == null || session.getHandshakeInfo() == null) {
            return "";
        }
        HttpHeaders headers = session.getHandshakeInfo().getHeaders();
        String traceId = headers.getFirst(HEADER_TRACE_ID);
        return traceId == null ? "" : traceId.trim();
    }

    private static String safeReason(Throwable ex) {
        return ex == null || ex.getMessage() == null ? "" : ex.getMessage();
    }

    private static void routeInboundMessage(
            AtomicBoolean firstSeen,
            Sinks.One<InboundFrame> firstMessage,
            Sinks.Many<InboundFrame> subsequentFrames,
            WebSocketMessage message
    ) {
        InboundFrame frame = InboundFrame.from(message);
        if (firstSeen.compareAndSet(false, true)) {
            firstMessage.tryEmitValue(frame);
            return;
        }
        subsequentFrames.tryEmitNext(frame);
    }

    private static void failInbound(
            AtomicBoolean firstSeen,
            Sinks.One<InboundFrame> firstMessage,
            Sinks.Many<InboundFrame> subsequentFrames,
            Throwable ex
    ) {
        if (!firstSeen.get()) {
            firstMessage.tryEmitError(ex);
        }
        subsequentFrames.tryEmitError(ex);
    }

    private static void completeInbound(
            AtomicBoolean firstSeen,
            Sinks.One<InboundFrame> firstMessage,
            Sinks.Many<InboundFrame> subsequentFrames
    ) {
        if (!firstSeen.get()) {
            firstMessage.tryEmitEmpty();
        }
        subsequentFrames.tryEmitComplete();
    }

    private static class UnsupportedFrameTypeException extends RuntimeException {

        UnsupportedFrameTypeException(WebSocketMessage.Type type) {
            super("unsupported websocket frame type: " + type);
        }
    }

    private record InboundFrame(WebSocketMessage.Type type, String text) {

        private static InboundFrame from(WebSocketMessage message) {
            if (message.getType() == WebSocketMessage.Type.TEXT) {
                return new InboundFrame(message.getType(), message.getPayloadAsText());
            }
            return new InboundFrame(message.getType(), "");
        }
    }
}
