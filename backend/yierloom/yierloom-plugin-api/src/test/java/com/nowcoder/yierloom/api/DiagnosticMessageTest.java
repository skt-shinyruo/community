package com.nowcoder.yierloom.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosticMessageTest {

    @Test
    void keepsAllScalarFieldMapsImmutableAndKeysTypeUnique() {
        DiagnosticEvent event = DiagnosticEvent.builder("method_slow_call")
                .attribute("method.name", "work")
                .attribute("event.outcome", "threshold")
                .attribute("duration.ms", "old")
                .longField("duration.ms", 25)
                .build();

        assertThat(event.action()).isEqualTo("method_slow_call");
        assertThat(event.attributes()).containsEntry("method.name", "work")
                .doesNotContainKey("duration.ms");
        assertThat(event.longFields()).containsEntry("duration.ms", 25L);
        assertThatThrownBy(() -> event.attributes().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
