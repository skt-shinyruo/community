package com.nowcoder.community.im.gateway.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.im.common.ws.ConnectFrame;
import com.nowcoder.community.im.gateway.session.SessionTicketCodec;
import com.nowcoder.community.im.gateway.shard.WorkerDescriptor;
import com.nowcoder.community.im.gateway.shard.WorkerRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;

@Component
public class ConnectTicketRouter {

    private static final String TYPE_CONNECT = "connect";

    private final ImGatewayFrameCodec frameCodec;
    private final SessionTicketCodec sessionTicketCodec;
    private final WorkerRegistry workerRegistry;

    public ConnectTicketRouter(
            ImGatewayFrameCodec frameCodec,
            SessionTicketCodec sessionTicketCodec,
            WorkerRegistry workerRegistry
    ) {
        this.frameCodec = frameCodec;
        this.sessionTicketCodec = sessionTicketCodec;
        this.workerRegistry = workerRegistry;
    }

    public RoutingDecision route(String firstFrame) {
        JsonNode node = readFirstFrame(firstFrame);
        if (!TYPE_CONNECT.equals(node.path("type").asText(""))) {
            throw new RoutingException(401, "connect_required", "connect required");
        }
        ConnectFrame frame = readConnectFrame(node);
        SessionTicketCodec.TicketClaims claims = decodeTicket(frame.ticket());
        WorkerDescriptor worker = findWorker(claims.workerId());
        return new RoutingDecision(claims.sessionId(), claims.workerId(), worker.getUri());
    }

    private JsonNode readFirstFrame(String firstFrame) {
        if (!StringUtils.hasText(firstFrame)) {
            throw new RoutingException(400, "malformed_frame", "malformed frame");
        }
        try {
            return frameCodec.readTree(firstFrame);
        } catch (RuntimeException ex) {
            throw new RoutingException(400, "malformed_frame", "malformed frame", ex);
        }
    }

    private ConnectFrame readConnectFrame(JsonNode node) {
        try {
            return frameCodec.read(node, ConnectFrame.class);
        } catch (RuntimeException ex) {
            throw invalidTicket(ex);
        }
    }

    private SessionTicketCodec.TicketClaims decodeTicket(String ticket) {
        try {
            return sessionTicketCodec.decode(ticket);
        } catch (RuntimeException ex) {
            throw invalidTicket(ex);
        }
    }

    private WorkerDescriptor findWorker(String workerId) {
        try {
            return workerRegistry.find(workerId)
                    .orElseThrow(() -> new RoutingException(503, "worker_unavailable", "worker unavailable"));
        } catch (RoutingException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new RoutingException(503, "worker_unavailable", "worker unavailable", ex);
        }
    }

    private static RoutingException invalidTicket(RuntimeException cause) {
        return new RoutingException(401, "invalid_ticket", "invalid ticket", cause);
    }

    public record RoutingDecision(String sessionId, String workerId, URI workerUri) {
    }

    public static class RoutingException extends RuntimeException {

        private final int code;
        private final String reasonCode;

        public RoutingException(int code, String reasonCode, String message) {
            super(message);
            this.code = code;
            this.reasonCode = reasonCode;
        }

        public RoutingException(int code, String reasonCode, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.reasonCode = reasonCode;
        }

        public int code() {
            return code;
        }

        public String reasonCode() {
            return reasonCode;
        }
    }
}
