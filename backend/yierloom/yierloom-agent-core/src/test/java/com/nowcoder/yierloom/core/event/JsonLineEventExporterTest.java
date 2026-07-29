package com.nowcoder.yierloom.core.event;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLineEventExporterTest {

    @Test
    void writesOneEscapedLineWithCoreOwnedReservedFieldsAndTypedValues() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Clock clock = Clock.fixed(Instant.parse("2026-07-29T10:15:30Z"), ZoneOffset.UTC);
        JsonLineEventExporter exporter = new JsonLineEventExporter(
                "community-app",
                clock,
                new PrintStream(output, true, StandardCharsets.UTF_8));
        DiagnosticEvent event = DiagnosticEvent.builder("method_slow_call")
                .timestamp(Instant.parse("2026-07-29T09:00:00Z"))
                .attribute("@timestamp", "attacker-time")
                .attribute("event.category", "attacker-category")
                .attribute("event.action", "attacker-action")
                .attribute("diagnostic.agent.name", "attacker-agent")
                .attribute("diagnostic.plugin.id", "attacker-plugin")
                .attribute("service.name", "attacker-service")
                .attribute("message", "quoted \"line\"\\next\nrow")
                .booleanField("sampled", true)
                .longField("duration.ms", 25)
                .doubleField("ratio", 0.5)
                .doubleField("not-a-number", Double.NaN)
                .doubleField("positive-infinity", Double.POSITIVE_INFINITY)
                .build();

        exporter.export("http", event);

        String json = output.toString(StandardCharsets.UTF_8);
        assertThat(json).endsWith(System.lineSeparator());
        assertThat(json.lines()).hasSize(1);
        assertThat(json)
                .contains("\"@timestamp\":\"2026-07-29T09:00:00Z\"")
                .contains("\"event.category\":\"yierloom\"")
                .contains("\"event.action\":\"method_slow_call\"")
                .contains("\"diagnostic.agent.name\":\"YierLoom\"")
                .contains("\"diagnostic.plugin.id\":\"http\"")
                .contains("\"service.name\":\"community-app\"")
                .contains("\"message\":\"quoted \\\"line\\\"\\\\next\\nrow\"")
                .contains("\"sampled\":true")
                .contains("\"duration.ms\":25")
                .contains("\"ratio\":0.5")
                .contains("\"not-a-number\":\"NaN\"")
                .contains("\"positive-infinity\":\"Infinity\"")
                .doesNotContain("attacker-");
    }

    @Test
    void usesClockWhenTimestampIsAbsent() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Clock clock = Clock.fixed(Instant.parse("2026-07-29T10:15:30Z"), ZoneOffset.UTC);
        JsonLineEventExporter exporter = new JsonLineEventExporter(
                "community-app",
                clock,
                new PrintStream(output, true, StandardCharsets.UTF_8));

        exporter.export("jvm", DiagnosticEvent.builder("jvm_summary").build());

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("\"@timestamp\":\"2026-07-29T10:15:30Z\"");
    }
}
