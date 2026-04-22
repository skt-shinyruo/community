package com.nowcoder.community.common.id;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidV7GeneratorTest {

    @Test
    void next_shouldGenerateUuidVersion7Values() {
        UuidV7Generator generator = new UuidV7Generator(Clock.systemUTC());

        UUID value = generator.next();

        assertEquals(7, value.version());
        assertEquals(2, value.variant());
    }

    @Test
    void next_shouldRemainSortedWhenClockDoesNotAdvance() {
        Instant fixedInstant = Instant.parse("2026-04-20T12:34:56.789Z");
        UuidV7Generator generator = new UuidV7Generator(Clock.fixed(fixedInstant, ZoneOffset.UTC));

        UUID first = generator.next();
        UUID second = generator.next();
        UUID third = generator.next();

        assertTrue(Arrays.compareUnsigned(BinaryUuidCodec.toBytes(first), BinaryUuidCodec.toBytes(second)) < 0);
        assertTrue(Arrays.compareUnsigned(BinaryUuidCodec.toBytes(second), BinaryUuidCodec.toBytes(third)) < 0);
        assertNotEquals(first, second);
        assertNotEquals(second, third);
    }

    @Test
    void codec_shouldRoundTripUuidBinaryRepresentation() {
        UuidV7Generator generator = new UuidV7Generator(Clock.fixed(Instant.parse("2026-04-20T12:34:56.789Z"), ZoneOffset.UTC));
        UUID original = generator.next();

        byte[] bytes = BinaryUuidCodec.toBytes(original);
        UUID restored = BinaryUuidCodec.fromBytes(bytes);

        assertEquals(16, bytes.length);
        assertEquals(original, restored);
        assertArrayEquals(bytes, BinaryUuidCodec.toBytes(restored));
    }

    @Test
    void next_shouldGenerateDistinctValuesAcrossBurst() {
        UuidV7Generator generator = new UuidV7Generator(Clock.fixed(Instant.parse("2026-04-20T12:34:56.789Z"), ZoneOffset.UTC));
        Set<UUID> values = new HashSet<>();

        for (int index = 0; index < 32; index++) {
            values.add(generator.next());
        }

        assertEquals(32, values.size());
    }
}
