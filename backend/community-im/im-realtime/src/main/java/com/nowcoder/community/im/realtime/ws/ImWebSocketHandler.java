package com.nowcoder.community.im.realtime.ws;

import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceIdCodec;
import com.nowcoder.community.im.realtime.frame.RealtimeFrameHandler;
import com.nowcoder.community.im.realtime.service.ConnectionLifecycleService;
import com.nowcoder.community.im.realtime.session.ConnectionSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Realtime transport adapter: the only module that touches the WebSocket session and
 * Reactor sink. Per socket it creates a {@link WsConnectionOutput} (production output)
 * plus a transport-free {@link ConnectionSession}, pumps inbound text into the frame
 * module and lets the lifecycle service orchestrate cleanup. Frame semantics live in
 * {@link RealtimeFrameHandler}; room binding/presence orchestration in
 * {@link ConnectionLifecycleService}.
 */
@Component
public class ImWebSocketHandler implements WebSocketHandler {

    private final RealtimeFrameHandler frameHandler;
    private final ConnectionLifecycleService connectionLifecycleService;
    private final int maxOutboundBacklog;

    public ImWebSocketHandler(
            RealtimeFrameHandler frameHandler,
            ConnectionLifecycleService connectionLifecycleService,
            @Value("${im.ws.outbound-buffer-size:256}") int maxOutboundBacklog
    ) {
        this.frameHandler = frameHandler;
        this.connectionLifecycleService = connectionLifecycleService;
        this.maxOutboundBacklog = Math.min(Math.max(1, maxOutboundBacklog), 10_000);
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        WsConnectionOutput output = new WsConnectionOutput(session, maxOutboundBacklog);
        ConnectionSession conn = new ConnectionSession(session.getId(), output);
        conn.bindTrace(resolveTraceId(session));

        AtomicBoolean cleaned = new AtomicBoolean(false);
        Runnable cleanupOnce = () -> {
            if (cleaned.compareAndSet(false, true)) {
                connectionLifecycleService.disconnect(conn);
            }
        };

        Mono<Void> sender = output.sendToSession().doFinally(signalType -> cleanupOnce.run());
        Mono<Void> receiver = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(text -> frameHandler.handleInboundText(conn, text))
                .doFinally(signalType -> cleanupOnce.run())
                .then();

        return Mono.when(sender, receiver);
    }

    private String resolveTraceId(WebSocketSession session) {
        if (session == null || session.getHandshakeInfo() == null || session.getHandshakeInfo().getHeaders() == null) {
            return TraceIdCodec.generateTraceId();
        }
        String traceparentHeader = session.getHandshakeInfo().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT);
        return TraceIdCodec.resolveTraceId(traceparentHeader);
    }
}
