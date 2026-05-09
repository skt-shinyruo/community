package com.nowcoder.community.drive.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriveSpaceTest {

    @Test
    void defaultSpaceShouldStartWithTenGiBQuotaAndNoUsage() {
        DriveSpace space = DriveSpace.createDefault(uuid(1), uuid(7), Instant.parse("2026-05-09T00:00:00Z"));

        assertThat(space.quotaBytes()).isEqualTo(10_737_418_240L);
        assertThat(space.usedBytes()).isZero();
        assertThat(space.remainingBytes()).isEqualTo(10_737_418_240L);
    }

    @Test
    void reserveAndReleaseShouldProtectQuotaBounds() {
        DriveSpace space = DriveSpace.createDefault(uuid(1), uuid(7), Instant.parse("2026-05-09T00:00:00Z"));

        DriveSpace reserved = space.reserve(1_024L, Instant.parse("2026-05-09T00:01:00Z"));
        assertThat(reserved.usedBytes()).isEqualTo(1_024L);
        assertThat(reserved.release(512L, Instant.parse("2026-05-09T00:02:00Z")).usedBytes()).isEqualTo(512L);

        assertThatThrownBy(() -> space.reserve(10_737_418_241L, Instant.parse("2026-05-09T00:03:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("drive quota exceeded");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
