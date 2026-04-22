package com.nowcoder.community.common.id;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class BinaryUuidCodec {

    private BinaryUuidCodec() {
    }

    public static byte[] toBytes(UUID value) {
        if (value == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    public static UUID fromBytes(byte[] value) {
        if (value == null) {
            return null;
        }
        if (value.length != 16) {
            throw new IllegalArgumentException("UUID binary value must be 16 bytes");
        }
        ByteBuffer buffer = ByteBuffer.wrap(value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
