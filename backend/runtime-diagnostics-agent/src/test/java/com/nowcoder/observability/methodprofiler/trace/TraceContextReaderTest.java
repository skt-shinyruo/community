package com.nowcoder.observability.methodprofiler.trace;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextReaderTest {

    @Test
    void returnsEmptyWhenNoOptionalTraceLibrariesAreAvailableOrActive() {
        TraceContextReader reader = new TraceContextReader();

        Map<String, String> fields = reader.currentTraceFields();

        assertThat(fields).doesNotContainKeys("trace.id", "span.id");
    }
}
