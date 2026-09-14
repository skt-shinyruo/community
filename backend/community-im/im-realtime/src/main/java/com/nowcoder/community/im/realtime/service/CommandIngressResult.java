package com.nowcoder.community.im.realtime.service;

/**
 * Terminal outcome of one command enqueue attempt in {@link MessageCommandIngressService}.
 *
 * <p>Every send attempt produces exactly one terminal: {@link Outcome#ACK} when the command
 * was accepted by Kafka, or {@link Outcome#REJECT} when the enqueue failed or timed out.
 * The result carries the attempt correlation identifiers ({@code cmd}, {@code clientMsgId},
 * {@code requestId}) so callers can map it to a WebSocket ack/reject frame without the
 * ingress touching the connection itself. {@code code}, {@code reasonCode} and
 * {@code message} are only meaningful for {@link Outcome#REJECT}.
 */
public record CommandIngressResult(
        String cmd,
        String clientMsgId,
        String requestId,
        Outcome outcome,
        int code,
        String reasonCode,
        String message
) {

    public enum Outcome {
        ACK,
        REJECT
    }

    public CommandIngressResult {
        clientMsgId = clientMsgId == null ? "" : clientMsgId;
        requestId = requestId == null ? "" : requestId;
        reasonCode = reasonCode == null ? "" : reasonCode;
        message = message == null ? "" : message;
    }

    public boolean acked() {
        return outcome == Outcome.ACK;
    }

    public static CommandIngressResult acked(String cmd, String clientMsgId, String requestId) {
        return new CommandIngressResult(cmd, clientMsgId, requestId, Outcome.ACK, 0, "", "");
    }

    public static CommandIngressResult rejected(
            String cmd,
            String clientMsgId,
            String requestId,
            int code,
            String reasonCode,
            String message
    ) {
        return new CommandIngressResult(cmd, clientMsgId, requestId, Outcome.REJECT, code, reasonCode, message);
    }
}
