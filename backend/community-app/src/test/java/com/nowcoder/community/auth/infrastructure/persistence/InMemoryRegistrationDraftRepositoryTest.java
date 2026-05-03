package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRegistrationDraftRepositoryTest {

    @Test
    void issueFindAndDeleteShouldRoundTripDraft() {
        InMemoryRegistrationDraftRepository repository = new InMemoryRegistrationDraftRepository();
        PreparedRegistrationDraft draft = draft();

        String token = repository.issue(draft, Duration.ofMinutes(30));

        assertThat(token).matches("[a-f0-9]{32}");
        assertThat(repository.find(token)).contains(draft);
        repository.delete(token);
        assertThat(repository.find(token)).isEmpty();
    }

    @Test
    void findShouldRemoveExpiredDraft() throws Exception {
        InMemoryRegistrationDraftRepository repository = new InMemoryRegistrationDraftRepository();
        String token = repository.issue(draft(), Duration.ofMillis(1));

        Thread.sleep(5);

        assertThat(repository.find(token)).isEmpty();
        assertThat(repository.find(token)).isEmpty();
    }

    private static PreparedRegistrationDraft draft() {
        Instant now = Instant.parse("2026-05-03T01:00:00Z");
        return new PreparedRegistrationDraft(
                UUID.fromString("00000000-0000-7000-8000-000000000007"),
                "alice",
                "alice@example.com",
                "encoded-password",
                "h",
                now,
                now.plusSeconds(1800)
        );
    }
}
