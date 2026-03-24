package com.nowcoder.community.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryRegistrationCodeStoreTest {

    @Test
    void issueShouldOverwritePreviousCodeForSameUserWhenCooldownAllows() {
        RegistrationCodeStore store = new InMemoryRegistrationCodeStore();

        assertThat(store.issue(42, "111111", Duration.ofMinutes(5), Duration.ZERO))
                .isEqualTo(RegistrationCodeStore.IssueResult.ISSUED);
        assertThat(store.issue(42, "222222", Duration.ofMinutes(5), Duration.ZERO))
                .isEqualTo(RegistrationCodeStore.IssueResult.ISSUED);

        assertThat(store.verifyAndConsume(42, "111111"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.MISMATCH);
        assertThat(store.verifyAndConsume(42, "222222"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.SUCCESS);
    }

    @Test
    void verifyAndConsumeShouldSucceedOnlyOnce() {
        RegistrationCodeStore store = new InMemoryRegistrationCodeStore();

        store.issue(42, "222222", Duration.ofMinutes(5), Duration.ZERO);

        assertThat(store.verifyAndConsume(42, "222222"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.SUCCESS);
        assertThat(store.verifyAndConsume(42, "222222"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.NOT_FOUND);
    }

    @Test
    void issueShouldRejectWhenCooldownWindowIsStillActive() {
        RegistrationCodeStore store = new InMemoryRegistrationCodeStore();

        assertThat(store.issue(42, "222222", Duration.ofMinutes(5), Duration.ofMinutes(1)))
                .isEqualTo(RegistrationCodeStore.IssueResult.ISSUED);
        assertThat(store.issue(42, "333333", Duration.ofMinutes(5), Duration.ofMinutes(1)))
                .isEqualTo(RegistrationCodeStore.IssueResult.COOLDOWN_ACTIVE);

        assertThat(store.verifyAndConsume(42, "222222"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.SUCCESS);
    }

    @Test
    void mismatchShouldNotConsumeValidCode() {
        RegistrationCodeStore store = new InMemoryRegistrationCodeStore();

        store.issue(42, "222222", Duration.ofMinutes(5), Duration.ZERO);

        assertThat(store.verifyAndConsume(42, "111111"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.MISMATCH);
        assertThat(store.verifyAndConsume(42, "222222"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.SUCCESS);
    }

    @Test
    void tooManyMismatchesShouldEventuallyInvalidateCurrentCode() {
        RegistrationCodeStore store = new InMemoryRegistrationCodeStore();

        store.issue(42, "222222", Duration.ofMinutes(5), Duration.ZERO);

        RegistrationCodeStore.VerifyResult result = null;
        for (int i = 0; i < 10; i++) {
            result = store.verifyAndConsume(42, "wrong-" + i);
            if (result == RegistrationCodeStore.VerifyResult.TOO_MANY_ATTEMPTS) {
                break;
            }
            assertThat(result).isEqualTo(RegistrationCodeStore.VerifyResult.MISMATCH);
        }

        assertThat(result).isEqualTo(RegistrationCodeStore.VerifyResult.TOO_MANY_ATTEMPTS);
        assertThat(store.verifyAndConsume(42, "222222"))
                .isNotEqualTo(RegistrationCodeStore.VerifyResult.SUCCESS);
    }

    @Test
    void expiredCodeShouldBeRejected() throws InterruptedException {
        RegistrationCodeStore store = new InMemoryRegistrationCodeStore();

        store.issue(42, "222222", Duration.ofMillis(1), Duration.ZERO);
        Thread.sleep(25);

        assertThat(store.verifyAndConsume(42, "222222"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.EXPIRED);
        assertThat(store.issue(42, "333333", Duration.ofMinutes(5), Duration.ofMinutes(1)))
                .isEqualTo(RegistrationCodeStore.IssueResult.ISSUED);
    }
}
