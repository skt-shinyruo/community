package com.nowcoder.community.im.common.event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public final class ImEventIds {

    private static final int ATTEMPT_DIGEST_BYTES = 16;

    private ImEventIds() {
    }

    public static String privateMessageFact(UUID messageId) {
        return "im:pf:" + requireUuid(messageId, "messageId");
    }

    public static String roomMessageFact(UUID roomId, long seq) {
        if (seq <= 0) {
            throw new IllegalArgumentException("seq must be positive");
        }
        return "im:rf:" + requireUuid(roomId, "roomId") + ":" + seq;
    }

    public static String privateSendResult(String requestId, String clientMsgId, UUID fromUserId) {
        return "im:psr:" + attemptDigest(requestId, clientMsgId, fromUserId);
    }

    public static String roomSendResult(String requestId, String clientMsgId, UUID fromUserId) {
        return "im:rsr:" + attemptDigest(requestId, clientMsgId, fromUserId);
    }

    private static String attemptDigest(String requestId, String clientMsgId, UUID fromUserId) {
        String source = requireUuid(fromUserId, "fromUserId")
                + "|" + requireText(requestId, "requestId")
                + "|" + normalize(clientMsgId);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, ATTEMPT_DIGEST_BYTES);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    private static String requireUuid(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " required");
        }
        return value.toString();
    }

    private static String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " required");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
