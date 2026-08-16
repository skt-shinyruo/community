package com.nowcoder.community.common.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventLogMessageTest {

    @Test
    void shouldEncodeTokenSeparatorsAndInvisibleCharacters() {
        assertThat(EventLogMessage.format(
                "space", "a b",
                "empty", null,
                "separator", "a=b%",
                "invisible", "a\u200Bb"
        )).isEqualTo("space=a%20b empty=- separator=a%3Db%25 invisible=a%200Bb");

        assertThatThrownBy(() -> EventLogMessage.format("missing-value"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
