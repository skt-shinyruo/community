package com.nowcoder.community.im.realtime.ws;

import com.nowcoder.community.im.realtime.session.ConnectionOutput;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production {@link ConnectionOutput}: the only place the WebSocket session and its
 * Reactor sink live. Frames are buffered through a unicast backpressure-buffered sink;
 * when the undelivered backlog exceeds {@code maxOutboundBacklog} the connection is
 * closed instead of growing memory without bound.
 */
public class WsConnectionOutput implements ConnectionOutput {

    private final WebSocketSession session;
    private final Sinks.Many<String> outbound;
    private final AtomicInteger outboundBacklog;
    private final int maxOutboundBacklog;

    public WsConnectionOutput(WebSocketSession session, int maxOutboundBacklog) {
        this.session = session;
        this.maxOutboundBacklog = Math.max(1, maxOutboundBacklog);
        this.outbound = Sinks.many().unicast().onBackpressureBuffer();
        this.outboundBacklog = new AtomicInteger(0);
    }

    /** Streams buffered frames to the socket until the sink completes. */
    public Mono<Void> sendToSession() {
        return session.send(outbound.asFlux()
                .doOnNext(msg -> onOutboundDelivered())
                .map(session::textMessage));
    }

    @Override
    public boolean trySendText(String text) {
        if (text == null) {
            return true;
        }
        int afterInc = outboundBacklog.incrementAndGet();
        if (afterInc > maxOutboundBacklog) {
            outboundBacklog.decrementAndGet();
            closeAsync(Duration.ofSeconds(1));
            return false;
        }

        Sinks.EmitResult result = outbound.tryEmitNext(text);
        if (result.isFailure()) {
            outboundBacklog.decrementAndGet();
            return false;
        }
        return true;
    }

    @Override
    public void complete() {
        outbound.tryEmitComplete();
    }

    @Override
    public void closeAsync(Duration timeout) {
        try {
            session.close()
                    .timeout(timeout == null ? Duration.ofSeconds(1) : timeout)
                    .onErrorResume(e -> Mono.empty())
                    .subscribe();
        } catch (RuntimeException ignore) {
        }
    }

    @Override
    public int outboundBacklog() {
        return outboundBacklog.get();
    }

    private void onOutboundDelivered() {
        outboundBacklog.decrementAndGet();
    }
}
