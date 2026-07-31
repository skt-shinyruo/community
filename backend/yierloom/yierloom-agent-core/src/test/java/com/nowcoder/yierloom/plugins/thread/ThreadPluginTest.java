package com.nowcoder.yierloom.plugins.thread;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginConfigurationException;
import com.nowcoder.yierloom.api.RuntimeCapability;
import com.nowcoder.yierloom.plugins.PluginTestContext;
import com.nowcoder.yierloom.sdk.InstrumentationCapability;
import com.nowcoder.yierloom.testkit.PluginContractVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThreadPluginTest {

    @Test
    void reportsAggregatedStatesLocksAndDeadlocksWithoutSensitiveThreadData() {
        PluginTestContext context = new PluginTestContext(Map.of());
        ThreadSnapshotReporter reporter = new ThreadSnapshotReporter(() ->
                new ThreadSnapshotReporter.Snapshot(
                        4,
                        List.of(
                                new ThreadSnapshotReporter.ThreadSample(
                                        Thread.State.RUNNABLE, false),
                                new ThreadSnapshotReporter.ThreadSample(
                                        Thread.State.BLOCKED, false),
                                new ThreadSnapshotReporter.ThreadSample(
                                        Thread.State.WAITING, true),
                                new ThreadSnapshotReporter.ThreadSample(
                                        Thread.State.TIMED_WAITING, false)),
                        2));

        reporter.report(context.events());

        DiagnosticEvent event = context.singleEvent("thread_snapshot");
        assertThat(event.attributes()).containsExactlyEntriesOf(Map.of(
                "event.outcome", "snapshot"));
        assertThat(event.booleanFields()).isEmpty();
        assertThat(event.doubleFields()).isEmpty();
        assertThat(event.longFields()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "thread.count", 4L,
                "thread.state.runnable", 1L,
                "thread.state.blocked", 1L,
                "thread.state.waiting", 1L,
                "thread.state.timed_waiting", 1L,
                "thread.deadlock.count", 2L,
                "thread.lock.wait.count", 2L));
        assertThat(Stream.of(
                        event.attributes().keySet(),
                        event.booleanFields().keySet(),
                        event.longFields().keySet(),
                        event.doubleFields().keySet())
                .flatMap(java.util.Collection::stream))
                .noneMatch(key -> key.contains("name")
                        || key.contains("stack")
                        || key.contains("lock.name"));
    }

    @Test
    void neverEmitsNamesStacksOrLockNamesReadFromTheThreadMxBean() throws Exception {
        SensitiveLockName lock = new SensitiveLockName();
        CountDownLatch waiting = new CountDownLatch(1);
        Thread thread = new Thread(
                new SensitiveStackRunnable(lock, waiting),
                "sensitive-thread-name");
        thread.setDaemon(true);
        thread.start();
        try {
            assertThat(waiting.await(5, TimeUnit.SECONDS)).isTrue();
            waitForState(thread, Thread.State.WAITING);
            PluginTestContext context = new PluginTestContext(Map.of());
            new ThreadSnapshotReporter(ManagementFactory.getThreadMXBean())
                    .report(context.events());

            assertThat(context.singleEvent("thread_snapshot").toString())
                    .doesNotContain(
                            "sensitive-thread-name",
                            "SensitiveStackRunnable",
                            "SensitiveLockName");
        } finally {
            synchronized (lock) {
                lock.notifyAll();
            }
            thread.join(TimeUnit.SECONDS.toMillis(5));
        }
        assertThat(thread.isAlive()).isFalse();
    }

    @Test
    void declaresRuntimeOnlyDefaultsAndOwnsOneManagedTask() throws Exception {
        ThreadPlugin plugin = new ThreadPlugin();
        PluginTestContext context = new PluginTestContext(Map.of(
                "snapshot-interval", "2s"));

        assertThat(plugin).isInstanceOf(RuntimeCapability.class)
                .isNotInstanceOf(InstrumentationCapability.class);
        assertThat(plugin.descriptor().id()).isEqualTo("thread");
        assertThat(plugin.descriptor().name()).isEqualTo("Thread Diagnostics");
        assertThat(plugin.descriptor().version()).isEqualTo("1.0.0");
        assertThat(plugin.descriptor().apiVersion()).isEqualTo("1.0.0");
        assertThat(plugin.descriptor().defaultEnabled()).isTrue();
        assertThat(plugin.descriptor().order()).isEqualTo(300);

        plugin.start(context);

        assertThat(context.scheduledTaskNames()).containsExactly("thread-snapshot");
        assertThat(context.initialDelay("thread-snapshot")).isEqualTo(Duration.ofSeconds(2));
        assertThat(context.delay("thread-snapshot")).isEqualTo(Duration.ofSeconds(2));
        context.runTask("thread-snapshot");
        assertThat(context.events("thread_snapshot")).hasSize(1);

        plugin.stop();
        plugin.stop();
        assertThat(context.isTaskCancelled("thread-snapshot")).isTrue();
        context.runTask("thread-snapshot");
        assertThat(context.events("thread_snapshot")).hasSize(1);
    }

    @Test
    void usesTheSixtySecondSnapshotIntervalByDefault() throws Exception {
        ThreadPlugin plugin = new ThreadPlugin();
        PluginTestContext context = new PluginTestContext(Map.of());

        plugin.start(context);

        assertThat(context.initialDelay("thread-snapshot")).isEqualTo(Duration.ofSeconds(60));
        assertThat(context.delay("thread-snapshot")).isEqualTo(Duration.ofSeconds(60));
        plugin.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1s"})
    void rejectsNonPositiveSnapshotIntervals(String interval) {
        ThreadPlugin plugin = new ThreadPlugin();

        assertThatThrownBy(() -> plugin.start(new PluginTestContext(Map.of(
                "snapshot-interval", interval))))
                .isInstanceOf(PluginConfigurationException.class)
                .hasMessageContaining("snapshot-interval")
                .hasMessageNotContaining(interval);
    }

    @Test
    void passesTheBuiltInPluginContractVerifier() {
        assertThat(PluginContractVerifier.verifyProvider(
                new ThreadPlugin(),
                PluginConfig.of(Map.of("snapshot-interval", "1s"))).valid()).isTrue();
    }

    private static void waitForState(Thread thread, Thread.State expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != expected && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(1);
        }
        assertThat(thread.getState()).isEqualTo(expected);
    }

    private static final class SensitiveLockName {
    }

    private record SensitiveStackRunnable(
            SensitiveLockName lock,
            CountDownLatch waiting
    ) implements Runnable {
        @Override
        public void run() {
            synchronized (lock) {
                waiting.countDown();
                try {
                    lock.wait();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
