package com.nowcoder.community.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryRegistrationCodeStoreTest {

    @Test
    void issueShouldOverwritePreviousCodeForSameUserWhenCooldownAllows() {
        UUID userId = uuid(42);
        RegistrationCodeStore store = new InMemoryRegistrationCodeStore();

        assertThat(store.issue(userId, "111111", Duration.ofMinutes(5), Duration.ZERO))
                .isEqualTo(RegistrationCodeStore.IssueResult.ISSUED);
        assertThat(store.issue(userId, "222222", Duration.ofMinutes(5), Duration.ZERO))
                .isEqualTo(RegistrationCodeStore.IssueResult.ISSUED);

        assertThat(store.verifyAndConsume(userId, "111111"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.MISMATCH);
        assertThat(store.verifyAndConsume(userId, "222222"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.SUCCESS);
    }

    @Test
    void verifyAndConsumeShouldSucceedOnlyOnce() {
        UUID userId = uuid(42);
        RegistrationCodeStore store = new InMemoryRegistrationCodeStore();

        store.issue(userId, "222222", Duration.ofMinutes(5), Duration.ZERO);

        assertThat(store.verifyAndConsume(userId, "222222"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.SUCCESS);
        assertThat(store.verifyAndConsume(userId, "222222"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.NOT_FOUND);
    }

    @Test
    void issueShouldRejectWhenCooldownWindowIsStillActive() {
        UUID userId = uuid(42);
        RegistrationCodeStore store = new InMemoryRegistrationCodeStore();

        assertThat(store.issue(userId, "222222", Duration.ofMinutes(5), Duration.ofMinutes(1)))
                .isEqualTo(RegistrationCodeStore.IssueResult.ISSUED);
        assertThat(store.issue(userId, "333333", Duration.ofMinutes(5), Duration.ofMinutes(1)))
                .isEqualTo(RegistrationCodeStore.IssueResult.COOLDOWN_ACTIVE);

        assertThat(store.verifyAndConsume(userId, "222222"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.SUCCESS);
    }

    @Test
    void mismatchShouldNotConsumeValidCode() {
        UUID userId = uuid(42);
        RegistrationCodeStore store = new InMemoryRegistrationCodeStore();

        store.issue(userId, "222222", Duration.ofMinutes(5), Duration.ZERO);

        assertThat(store.verifyAndConsume(userId, "111111"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.MISMATCH);
        assertThat(store.verifyAndConsume(userId, "222222"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.SUCCESS);
    }

    @Test
    void tooManyMismatchesShouldEventuallyInvalidateCurrentCode() {
        UUID userId = uuid(42);
        RegistrationCodeStore store = new InMemoryRegistrationCodeStore();

        store.issue(userId, "222222", Duration.ofMinutes(5), Duration.ZERO);

        RegistrationCodeStore.VerifyResult result = null;
        for (int i = 0; i < 10; i++) {
            result = store.verifyAndConsume(userId, "wrong-" + i);
            if (result == RegistrationCodeStore.VerifyResult.TOO_MANY_ATTEMPTS) {
                break;
            }
            assertThat(result).isEqualTo(RegistrationCodeStore.VerifyResult.MISMATCH);
        }

        assertThat(result).isEqualTo(RegistrationCodeStore.VerifyResult.TOO_MANY_ATTEMPTS);
        assertThat(store.verifyAndConsume(userId, "222222"))
                .isNotEqualTo(RegistrationCodeStore.VerifyResult.SUCCESS);
    }

    @Test
    void expiredCodeShouldBeRejected() throws InterruptedException {
        UUID userId = uuid(42);
        RegistrationCodeStore store = new InMemoryRegistrationCodeStore();

        store.issue(userId, "222222", Duration.ofMillis(1), Duration.ZERO);
        Thread.sleep(25);

        assertThat(store.verifyAndConsume(userId, "222222"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.EXPIRED);
        assertThat(store.issue(userId, "333333", Duration.ofMinutes(5), Duration.ofMinutes(1)))
                .isEqualTo(RegistrationCodeStore.IssueResult.ISSUED);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
