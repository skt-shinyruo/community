package com.nowcoder.community.social.application;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

final class FollowCursorCodec {

    private static final byte VERSION = 1;
    private static final int PAYLOAD_BYTES = 25;

    String encode(Instant followTime, UUID targetId) {
        if (followTime == null || targetId == null || followTime.toEpochMilli() <= 0L) {
            throw new IllegalArgumentException("follow cursor boundary is incomplete");
        }
        ByteBuffer payload = ByteBuffer.allocate(PAYLOAD_BYTES)
                .put(VERSION)
                .putLong(followTime.toEpochMilli())
                .putLong(targetId.getMostSignificantBits())
                .putLong(targetId.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.array());
    }

    Optional<Boundary> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(cursor.trim());
            if (payload.length != PAYLOAD_BYTES) {
                throw new IllegalArgumentException("invalid follow cursor length");
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            if (buffer.get() != VERSION) {
                throw new IllegalArgumentException("unsupported follow cursor version");
            }
            long followTimeMillis = buffer.getLong();
            UUID targetId = new UUID(buffer.getLong(), buffer.getLong());
            if (followTimeMillis <= 0L) {
                throw new IllegalArgumentException("invalid follow cursor timestamp");
            }
            return Optional.of(new Boundary(Instant.ofEpochMilli(followTimeMillis), targetId));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid follow cursor", exception);
        }
    }

    record Boundary(Instant followTime, UUID targetId) {
    }
}
