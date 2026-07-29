package com.nowcoder.yierloom.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiVersionTest {

    @Test
    void comparesMajorAndMinorWhileIgnoringPatch() {
        assertThat(ApiVersion.isCompatible("1.2.9", "1.2.0")).isTrue();
        assertThat(ApiVersion.isCompatible("1.3.0", "1.2.9")).isTrue();
        assertThat(ApiVersion.isCompatible("2.2.0", "1.2.0")).isFalse();
        assertThat(ApiVersion.isCompatible("1.1.9", "1.2.0")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1.2", "1.2.3.4", "1.-2.3", "v1.2.3"})
    void rejectsVersionsWithoutExactlyThreeNonNegativeComponents(String version) {
        assertThatThrownBy(() -> ApiVersion.isCompatible(version, "1.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
