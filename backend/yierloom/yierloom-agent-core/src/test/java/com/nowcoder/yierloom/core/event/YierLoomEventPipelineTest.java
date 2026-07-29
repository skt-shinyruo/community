package com.nowcoder.yierloom.core.event;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.PluginObservation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class YierLoomEventPipelineTest {

    @Test
    void routesObservationOnlyToItsPluginAndExportsHandlerEvents() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YierLoomEventPipeline pipeline = new YierLoomEventPipeline(
                8,
                "community-app",
                Clock.systemUTC(),
                new PrintStream(out, true, StandardCharsets.UTF_8));
        CountDownLatch handled = new CountDownLatch(1);
        pipeline.registerPlugin("alpha");
        pipeline.observations("alpha").register(observation -> {
            pipeline.events("alpha").emit(DiagnosticEvent.builder("alpha_summary")
                    .longField("seen", observation.longFields().get("value"))
                    .build());
            handled.countDown();
        });
        pipeline.start();

        assertThat(pipeline.observe(
                "alpha",
                PluginObservation.builder("sample").longField("value", 3).build())).isTrue();
        assertThat(pipeline.observe("beta", PluginObservation.builder("sample").build())).isFalse();
        assertThat(handled.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(pipeline.drainAndClose(Duration.ofSeconds(2))).isTrue();
        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("\"event.category\":\"yierloom\"")
                .contains("\"event.action\":\"alpha_summary\"")
                .contains("\"diagnostic.plugin.id\":\"alpha\"")
                .contains("\"seen\":3");
    }

    @Test
    void fullQueueDropsWithoutBlocking() {
        YierLoomEventPipeline pipeline = pipelineWithCapacity(1);
        pipeline.registerPlugin("alpha");

        assertThat(pipeline.emit("alpha", DiagnosticEvent.builder("one").build())).isTrue();
        assertThat(pipeline.emit("alpha", DiagnosticEvent.builder("two").build())).isFalse();
        assertThat(pipeline.droppedMessages().events("alpha")).isEqualTo(1);
        assertThat(pipeline.droppedMessages().observations("alpha")).isZero();
    }

    @Test
    void exporterFailureDoesNotStopConsumerAndIsRateLimited() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-29T10:15:30Z"));
        List<String> reports = new ArrayList<>();
        CountDownLatch firstReport = new CountDownLatch(1);
        ExporterFailureReporter failures = new ExporterFailureReporter(clock, report -> {
            synchronized (reports) {
                reports.add(report);
            }
            firstReport.countDown();
        });
        CountDownLatch attempted = new CountDownLatch(2);
        YierLoomEventPipeline pipeline = new YierLoomEventPipeline(8, (pluginId, event) -> {
            attempted.countDown();
            throw new IllegalStateException("private event content");
        }, failures);
        pipeline.registerPlugin("alpha");
        pipeline.start();

        assertThat(pipeline.emit("alpha", DiagnosticEvent.builder("one").build())).isTrue();
        assertThat(pipeline.emit("alpha", DiagnosticEvent.builder("two").build())).isTrue();
        assertThat(attempted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(firstReport.await(2, TimeUnit.SECONDS)).isTrue();
        synchronized (reports) {
            assertThat(reports).hasSize(1);
            assertThat(reports.get(0)).doesNotContain("private event content");
        }
        assertThat(pipeline.consumerAlive()).isTrue();
        assertThat(pipeline.drainAndClose(Duration.ofSeconds(2))).isTrue();
    }

    @Test
    void failureReporterEmitsAtMostOneSummaryPerMinute() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-29T10:15:30Z"));
        List<String> reports = new ArrayList<>();
        ExporterFailureReporter failures = new ExporterFailureReporter(clock, reports::add);

        failures.report("json-lines", "write", new IllegalStateException("private-one"));
        failures.report("json-lines", "write", new IllegalStateException("private-two"));
        clock.advance(Duration.ofSeconds(59));
        failures.report("json-lines", "write", new IllegalStateException("private-three"));
        assertThat(reports).hasSize(1);
        clock.advance(Duration.ofSeconds(1));
        failures.report("json-lines", "write", new IllegalStateException("private-four"));

        assertThat(reports).hasSize(2);
        assertThat(reports).allSatisfy(report -> assertThat(report).doesNotContain("private-"));
    }

    @Test
    void permitsOnlyOneHandlerPerPluginAndRemovesItAfterThreeFailures() throws Exception {
        YierLoomEventPipeline pipeline = startedPipeline(8);
        CountDownLatch attempted = new CountDownLatch(3);
        pipeline.registerPlugin("alpha");
        pipeline.observations("alpha").register(observation -> {
            attempted.countDown();
            throw new IllegalStateException("handler failure");
        });

        assertThatThrownBy(() -> pipeline.observations("alpha").register(observation -> { }))
                .isInstanceOf(IllegalStateException.class);
        IntStream.range(0, 3).forEach(index -> assertThat(pipeline.observe(
                "alpha",
                PluginObservation.builder("failure").build())).isTrue());
        assertThat(attempted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(await(() -> !pipeline.hasHandler("alpha"), Duration.ofSeconds(2))).isTrue();
        assertThat(pipeline.observe(
                "alpha",
                PluginObservation.builder("after-removal").build())).isFalse();
        assertThat(pipeline.drainAndClose(Duration.ofSeconds(2))).isTrue();
    }

    @Test
    void stoppingObservationsStillAllowsFinalEvents() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YierLoomEventPipeline pipeline = new YierLoomEventPipeline(
                4,
                "community-app",
                Clock.systemUTC(),
                new PrintStream(out, true, StandardCharsets.UTF_8));
        pipeline.registerPlugin("alpha");
        pipeline.observations("alpha").register(observation -> { });
        pipeline.start();

        pipeline.stopObservations();

        assertThat(pipeline.observe("alpha", PluginObservation.builder("late").build())).isFalse();
        assertThat(pipeline.emit("alpha", DiagnosticEvent.builder("final_summary").build())).isTrue();
        assertThat(pipeline.drainAndClose(Duration.ofSeconds(2))).isTrue();
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("\"event.action\":\"final_summary\"");
    }

    @Test
    void timeoutCleanupWaitsForAdmissionAndCountsTheAcceptedEvent() throws Exception {
        YierLoomEventPipeline pipeline = pipelineWithCapacity(1);
        BlockingOfferQueue queue = new BlockingOfferQueue(1);
        replaceQueue(pipeline, queue);
        pipeline.registerPlugin("alpha");
        AtomicBoolean accepted = new AtomicBoolean();
        Thread producer = new Thread(() -> accepted.set(
                pipeline.emit("alpha", DiagnosticEvent.builder("late").build())));
        CountDownLatch shutdownFinished = new CountDownLatch(1);
        AtomicBoolean drained = new AtomicBoolean(true);
        Thread shutdown = new Thread(() -> {
            drained.set(pipeline.drainAndClose(Duration.ZERO));
            shutdownFinished.countDown();
        });

        producer.start();
        assertThat(queue.offerEntered.await(2, TimeUnit.SECONDS)).isTrue();
        shutdown.start();
        try {
            assertThat(shutdownFinished.await(200, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            queue.releaseOffers.countDown();
            producer.join(2_000);
            shutdown.join(2_000);
        }

        assertThat(producer.isAlive()).isFalse();
        assertThat(shutdown.isAlive()).isFalse();
        assertThat(accepted).isTrue();
        assertThat(drained).isFalse();
        assertThat(pipeline.droppedMessages().events("alpha")).isEqualTo(1);
    }

    @Test
    void drainTimeoutHasATwoSecondUpperLimit() throws Exception {
        CountDownLatch exporting = new CountDownLatch(1);
        CountDownLatch releaseExporter = new CountDownLatch(1);
        ExporterFailureReporter failures = new ExporterFailureReporter(Clock.systemUTC(), report -> { });
        YierLoomEventPipeline pipeline = new YierLoomEventPipeline(1, (pluginId, event) -> {
            exporting.countDown();
            try {
                releaseExporter.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, failures);
        pipeline.registerPlugin("alpha");
        pipeline.start();
        assertThat(pipeline.emit("alpha", DiagnosticEvent.builder("blocked").build())).isTrue();
        assertThat(exporting.await(2, TimeUnit.SECONDS)).isTrue();

        try {
            assertTimeoutPreemptively(Duration.ofMillis(2_500), () ->
                    assertThat(pipeline.drainAndClose(Duration.ofDays(1))).isFalse());
        } finally {
            releaseExporter.countDown();
        }
    }

    private static YierLoomEventPipeline pipelineWithCapacity(int capacity) {
        return new YierLoomEventPipeline(
                capacity,
                "test-service",
                Clock.systemUTC(),
                new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8));
    }

    private static YierLoomEventPipeline startedPipeline(int capacity) {
        YierLoomEventPipeline pipeline = pipelineWithCapacity(capacity);
        pipeline.start();
        return pipeline;
    }

    private static boolean await(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.yield();
        }
        return condition.getAsBoolean();
    }

    private static void replaceQueue(
            YierLoomEventPipeline pipeline,
            ArrayBlockingQueue<PipelineMessage> queue
    ) throws ReflectiveOperationException {
        Field queueField = YierLoomEventPipeline.class.getDeclaredField("queue");
        queueField.setAccessible(true);
        queueField.set(pipeline, queue);
    }

    private static final class BlockingOfferQueue extends ArrayBlockingQueue<PipelineMessage> {
        private final CountDownLatch offerEntered = new CountDownLatch(1);
        private final CountDownLatch releaseOffers = new CountDownLatch(1);

        private BlockingOfferQueue(int capacity) {
            super(capacity);
        }

        @Override
        public boolean offer(PipelineMessage message) {
            offerEntered.countDown();
            try {
                releaseOffers.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
            return super.offer(message);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
