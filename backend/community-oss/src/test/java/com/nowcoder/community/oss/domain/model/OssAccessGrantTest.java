package com.nowcoder.community.oss.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OssAccessGrantTest {

    @Test
    void readGrantShouldNormalizePermissionAndStopBeingActiveAfterRevocation() {
        Instant now = Instant.parse("2026-05-07T00:00:00Z");
        OssAccessGrant grant = OssAccessGrant.readGrant(
                uuid(1),
                uuid(2),
                uuid(3),
                " user ",
                " 7 ",
                " system ",
                now,
                now.plusSeconds(300)
        );

        assertThat(grant.principalType()).isEqualTo("USER");
        assertThat(grant.principalValue()).isEqualTo("7");
        assertThat(grant.permission()).isEqualTo("READ");
        assertThat(grant.createdBy()).isEqualTo("system");
        assertThat(grant.activeAt(now.plusSeconds(299))).isTrue();

        OssAccessGrant revoked = grant.revoke(now.plusSeconds(10));
        assertThat(revoked.revokedAt()).isEqualTo(now.plusSeconds(10));
        assertThat(revoked.activeAt(now.plusSeconds(11))).isFalse();
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
