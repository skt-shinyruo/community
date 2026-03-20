package com.nowcoder.community.common.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnknownEventActionTest {

    @Test
    void parseOrDefault_shouldUseDefaultForBlankOrInvalid() {
        assertThat(UnknownEventAction.parseOrDefault(null, UnknownEventAction.SKIP)).isEqualTo(UnknownEventAction.SKIP);
        assertThat(UnknownEventAction.parseOrDefault("", UnknownEventAction.DLQ)).isEqualTo(UnknownEventAction.DLQ);
        assertThat(UnknownEventAction.parseOrDefault("not-a-valid-action", UnknownEventAction.DLQ)).isEqualTo(UnknownEventAction.DLQ);
    }

    @Test
    void parseOrDefault_shouldParseCaseInsensitively() {
        assertThat(UnknownEventAction.parseOrDefault("dlq", UnknownEventAction.SKIP)).isEqualTo(UnknownEventAction.DLQ);
        assertThat(UnknownEventAction.parseOrDefault("SKIP", UnknownEventAction.DLQ)).isEqualTo(UnknownEventAction.SKIP);
    }
}

