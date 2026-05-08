package com.nowcoder.community.oss.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OssObjectAliasTest {

    @Test
    void activeAliasShouldNormalizeKeyAndPointAtVersion() {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        Instant now = Instant.parse("2026-05-07T00:00:00Z");

        OssObjectAlias alias = OssObjectAlias.active(
                " avatar/7/0123456789abcdef0123456789abcdef ",
                objectId,
                versionId,
                now
        );

        assertThat(alias.aliasKey()).isEqualTo("avatar/7/0123456789abcdef0123456789abcdef");
        assertThat(alias.objectId()).isEqualTo(objectId);
        assertThat(alias.versionId()).isEqualTo(versionId);
        assertThat(alias.status()).isEqualTo("ACTIVE");
        assertThat(alias.createdAt()).isEqualTo(now);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
