package com.nowcoder.community.auth.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRefreshTokenStoreTest {

    @Test
    void storeFindRevokeShouldWork() {
        InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore();

        Instant expiresAt = Instant.now().plusSeconds(3600);
        store.store("rt1", 1, "fam1", expiresAt);

        RefreshTokenStore.StoredRefreshToken found = store.find("rt1");
        assertThat(found).isNotNull();
        assertThat(found.refreshToken()).isEqualTo("rt1");
        assertThat(found.userId()).isEqualTo(1);
        assertThat(found.familyId()).isEqualTo("fam1");

        store.revoke("rt1");
        assertThat(store.find("rt1")).isNull();
    }

    @Test
    void revokeFamilyShouldInvalidateAllTokensInFamily() {
        InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore();

        Instant expiresAt = Instant.now().plusSeconds(3600);
        store.store("rt1", 1, "fam1", expiresAt);
        store.store("rt2", 1, "fam1", expiresAt);
        store.store("rt3", 2, "fam2", expiresAt);

        store.revokeFamily("fam1");

        assertThat(store.find("rt1")).isNull();
        assertThat(store.find("rt2")).isNull();
        assertThat(store.find("rt3")).isNotNull();
    }
}

