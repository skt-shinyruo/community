package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.RegistrationCodeRepository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryRegistrationCodeRepositoryTest {

    @Test
    void issueShouldOverwritePreviousCodeForSameUserWhenCooldownAllows() {
        UUID userId = uuid(42);
        RegistrationCodeRepository store = new InMemoryRegistrationCodeRepository();

        assertThat(store.issue(userId, "111111", Duration.ofMinutes(5), Duration.ZERO))
                .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
        assertThat(store.issue(userId, "222222", Duration.ofMinutes(5), Duration.ZERO))
                .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);

        assertThat(store.verifyAndConsume(userId, "111111"))
                .isEqualTo(RegistrationCodeRepository.VerifyResult.MISMATCH);
        assertThat(store.verifyAndConsume(userId, "222222"))
                .isEqualTo(RegistrationCodeRepository.VerifyResult.SUCCESS);
    }

    @Test
    void verifyAndConsumeShouldSucceedOnlyOnce() {
        UUID userId = uuid(42);
        RegistrationCodeRepository store = new InMemoryRegistrationCodeRepository();

        store.issue(userId, "222222", Duration.ofMinutes(5), Duration.ZERO);

        assertThat(store.verifyAndConsume(userId, "222222"))
                .isEqualTo(RegistrationCodeRepository.VerifyResult.SUCCESS);
        assertThat(store.verifyAndConsume(userId, "222222"))
                .isEqualTo(RegistrationCodeRepository.VerifyResult.NOT_FOUND);
    }

    @Test
    void issueShouldRejectWhenCooldownWindowIsStillActive() {
        UUID userId = uuid(42);
        RegistrationCodeRepository store = new InMemoryRegistrationCodeRepository();

        assertThat(store.issue(userId, "222222", Duration.ofMinutes(5), Duration.ofMinutes(1)))
                .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
        assertThat(store.issue(userId, "333333", Duration.ofMinutes(5), Duration.ofMinutes(1)))
                .isEqualTo(RegistrationCodeRepository.IssueResult.COOLDOWN_ACTIVE);

        assertThat(store.verifyAndConsume(userId, "222222"))
                .isEqualTo(RegistrationCodeRepository.VerifyResult.SUCCESS);
    }

    @Test
    void mismatchShouldNotConsumeValidCode() {
        UUID userId = uuid(42);
        RegistrationCodeRepository store = new InMemoryRegistrationCodeRepository();

        store.issue(userId, "222222", Duration.ofMinutes(5), Duration.ZERO);

        assertThat(store.verifyAndConsume(userId, "111111"))
                .isEqualTo(RegistrationCodeRepository.VerifyResult.MISMATCH);
        assertThat(store.verifyAndConsume(userId, "222222"))
                .isEqualTo(RegistrationCodeRepository.VerifyResult.SUCCESS);
    }

    @Test
    void tooManyMismatchesShouldEventuallyInvalidateCurrentCode() {
        UUID userId = uuid(42);
        RegistrationCodeRepository store = new InMemoryRegistrationCodeRepository();

        store.issue(userId, "222222", Duration.ofMinutes(5), Duration.ZERO);

        RegistrationCodeRepository.VerifyResult result = null;
        for (int i = 0; i < 10; i++) {
            result = store.verifyAndConsume(userId, "wrong-" + i);
            if (result == RegistrationCodeRepository.VerifyResult.TOO_MANY_ATTEMPTS) {
                break;
            }
            assertThat(result).isEqualTo(RegistrationCodeRepository.VerifyResult.MISMATCH);
        }

        assertThat(result).isEqualTo(RegistrationCodeRepository.VerifyResult.TOO_MANY_ATTEMPTS);
        assertThat(store.verifyAndConsume(userId, "222222"))
                .isNotEqualTo(RegistrationCodeRepository.VerifyResult.SUCCESS);
    }

    @Test
    void expiredCodeShouldBeRejected() throws InterruptedException {
        UUID userId = uuid(42);
        RegistrationCodeRepository store = new InMemoryRegistrationCodeRepository();

        store.issue(userId, "222222", Duration.ofMillis(1), Duration.ZERO);
        Thread.sleep(25);

        assertThat(store.verifyAndConsume(userId, "222222"))
                .isEqualTo(RegistrationCodeRepository.VerifyResult.EXPIRED);
        assertThat(store.issue(userId, "333333", Duration.ofMinutes(5), Duration.ofMinutes(1)))
                .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
