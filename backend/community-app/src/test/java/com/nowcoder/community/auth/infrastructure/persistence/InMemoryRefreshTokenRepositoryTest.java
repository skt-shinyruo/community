package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRefreshTokenRepositoryTest {

    @Test
    void storeFindRevokeShouldWork() {
        InMemoryRefreshTokenRepository store = new InMemoryRefreshTokenRepository();
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000001");

        Instant expiresAt = Instant.now().plusSeconds(3600);
        store.store("rt1", userId, "fam1", expiresAt);

        RefreshTokenRepository.StoredRefreshToken found = store.find("rt1");
        assertThat(found).isNotNull();
        assertThat(found.refreshToken()).isEqualTo("rt1");
        assertThat(found.userId()).isEqualTo(userId);
        assertThat(found.familyId()).isEqualTo("fam1");

        store.revoke("rt1");
        assertThat(store.find("rt1")).isNull();
    }

    @Test
    void revokeFamilyShouldInvalidateAllTokensInFamily() {
        InMemoryRefreshTokenRepository store = new InMemoryRefreshTokenRepository();
        UUID userId1 = UUID.fromString("00000000-0000-7000-8000-000000000001");
        UUID userId2 = UUID.fromString("00000000-0000-7000-8000-000000000002");

        Instant expiresAt = Instant.now().plusSeconds(3600);
        store.store("rt1", userId1, "fam1", expiresAt);
        store.store("rt2", userId1, "fam1", expiresAt);
        store.store("rt3", userId2, "fam2", expiresAt);

        store.revokeFamily("fam1");

        assertThat(store.find("rt1")).isNull();
        assertThat(store.find("rt2")).isNull();
        assertThat(store.find("rt3")).isNotNull();
    }
}
