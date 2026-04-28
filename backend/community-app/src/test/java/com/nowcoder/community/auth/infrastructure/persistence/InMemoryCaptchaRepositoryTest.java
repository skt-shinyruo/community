package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.CaptchaRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCaptchaRepositoryTest {

    @Test
    void verifyAndConsumeShouldInvalidateCaptchaAfterFirstSuccess() {
        InMemoryCaptchaRepository store = new InMemoryCaptchaRepository();
        store.save("cid", "AbC1", Duration.ofSeconds(60));

        assertThat(store.verifyAndConsume("cid", "abc1")).isEqualTo(CaptchaRepository.VerifyResult.MATCHED);
        assertThat(store.verifyAndConsume("cid", "abc1")).isEqualTo(CaptchaRepository.VerifyResult.NOT_FOUND);
    }
}
