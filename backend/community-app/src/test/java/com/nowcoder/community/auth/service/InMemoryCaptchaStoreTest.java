package com.nowcoder.community.auth.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCaptchaStoreTest {

    @Test
    void verifyAndConsumeShouldInvalidateCaptchaAfterFirstSuccess() {
        InMemoryCaptchaStore store = new InMemoryCaptchaStore();
        store.save("cid", "AbC1", Duration.ofSeconds(60));

        assertThat(store.verifyAndConsume("cid", "abc1")).isEqualTo(CaptchaStore.VerifyResult.MATCHED);
        assertThat(store.verifyAndConsume("cid", "abc1")).isEqualTo(CaptchaStore.VerifyResult.NOT_FOUND);
    }
}
