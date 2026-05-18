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
        assertThat(space.reservedBytes()).isZero();
        assertThat(space.remainingBytes()).isEqualTo(10_737_418_240L);
    }

    @Test
    void reserveCommitAndReleaseShouldProtectQuotaBounds() {
        DriveSpace space = DriveSpace.createDefault(uuid(1), uuid(7), Instant.parse("2026-05-09T00:00:00Z"));

        DriveSpace reserved = space.reserve(1_024L, Instant.parse("2026-05-09T00:01:00Z"));
        assertThat(reserved.usedBytes()).isZero();
        assertThat(reserved.reservedBytes()).isEqualTo(1_024L);
        assertThat(reserved.remainingBytes()).isEqualTo(10_737_417_216L);

        DriveSpace committed = reserved.commitReserved(512L, Instant.parse("2026-05-09T00:02:00Z"));
        assertThat(committed.usedBytes()).isEqualTo(512L);
        assertThat(committed.reservedBytes()).isEqualTo(512L);

        DriveSpace releasedReservation = committed.releaseReserved(256L, Instant.parse("2026-05-09T00:03:00Z"));
        assertThat(releasedReservation.usedBytes()).isEqualTo(512L);
        assertThat(releasedReservation.reservedBytes()).isEqualTo(256L);

        DriveSpace releasedUsedBytes = releasedReservation.release(128L, Instant.parse("2026-05-09T00:04:00Z"));
        assertThat(releasedUsedBytes.usedBytes()).isEqualTo(384L);
        assertThat(releasedUsedBytes.reservedBytes()).isEqualTo(256L);

        assertThatThrownBy(() -> space.reserve(10_737_418_241L, Instant.parse("2026-05-09T00:05:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("drive quota exceeded");
        assertThatThrownBy(() -> space.commitReserved(1L, Instant.parse("2026-05-09T00:06:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("drive quota reservation insufficient");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
