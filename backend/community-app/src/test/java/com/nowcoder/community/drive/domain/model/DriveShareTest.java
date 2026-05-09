package com.nowcoder.community.drive.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DriveShareTest {

    @Test
    void activeShareShouldRespectExpiryAndRevocation() {
        DriveShare share = DriveShare.active(
                uuid(1),
                uuid(2),
                "share-token",
                "hash",
                Instant.parse("2026-05-10T00:00:00Z"),
                uuid(7),
                Instant.parse("2026-05-09T00:00:00Z")
        );

        assertThat(share.activeAt(Instant.parse("2026-05-09T12:00:00Z"))).isTrue();
        assertThat(share.activeAt(Instant.parse("2026-05-10T00:00:01Z"))).isFalse();
        assertThat(share.revoke(Instant.parse("2026-05-09T13:00:00Z")).activeAt(Instant.parse("2026-05-09T13:00:01Z"))).isFalse();
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
