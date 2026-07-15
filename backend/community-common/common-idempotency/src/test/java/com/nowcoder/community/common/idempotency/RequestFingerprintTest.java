package com.nowcoder.community.common.idempotency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestFingerprintTest {

    @Test
    void sha256ShouldUseStableLowercaseUtf8Hex() {
        assertThat(RequestFingerprint.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
