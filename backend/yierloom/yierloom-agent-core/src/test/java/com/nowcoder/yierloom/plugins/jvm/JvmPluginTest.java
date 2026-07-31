package com.nowcoder.yierloom.plugins.jvm;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.EventSink;
import com.nowcoder.yierloom.api.ManagedScheduler;
import com.nowcoder.yierloom.api.ManagedTask;
import com.nowcoder.yierloom.api.ObservationChannel;
import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginConfigurationException;
import com.nowcoder.yierloom.api.PluginRuntimeContext;
import com.nowcoder.yierloom.api.RuntimeCapability;
import com.nowcoder.yierloom.sdk.InstrumentationCapability;
import com.nowcoder.yierloom.testkit.PluginContractVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JvmPluginTest {

    @Test
    void declaresStableDefaultEnabledRuntimeOnlyDescriptorAndSettings() {
        JvmPlugin plugin = new JvmPlugin();

        assertThat(plugin.descriptor().id()).isEqualTo("jvm");
        assertThat(plugin.descriptor().name()).isEqualTo("JVM Diagnostics");
        assertThat(plugin.descriptor().version()).isEqualTo("1.0.0");
        assertThat(plugin.descriptor().apiVersion()).isEqualTo("1.0.0");
        assertThat(plugin.descriptor().defaultEnabled()).isTrue();
        assertThat(plugin.descriptor().order()).isEqualTo(310);
        assertThat(plugin).isInstanceOf(RuntimeCapability.class);
        assertThat(plugin).isNotInstanceOf(InstrumentationCapability.class);

        assertThat(JvmPlugin.JvmSettings.from(PluginConfig.empty()).summaryInterval())
                .isEqualTo(Duration.ofSeconds(60));
        assertThat(JvmPlugin.JvmSettings.from(PluginConfig.of(Map.of(
                "enabled", "false",
                "summary-interval", "7s"))).summaryInterval())
                .isEqualTo(Duration.ofSeconds(7));
    }

    @Test
    void reportsRuntimeMemoryGcClassAndThreadMetricsWithoutSensitiveRuntimeData() {
        List<String> runtimeInvocations = new ArrayList<>();
        RuntimeMXBean runtime = proxy(
                RuntimeMXBean.class,
                Map.of(
                        "getUptime", -25L,
                        "getName", "sensitive-runtime-name",
                        "getInputArguments", List.of("-Dsecret=value"),
                        "getSystemProperties", Map.of("secret", "value")),
                runtimeInvocations);
        MemoryMXBean memory = proxy(MemoryMXBean.class, Map.of(
                "getHeapMemoryUsage", new MemoryUsage(-1, 101, 101, -1),
                "getNonHeapMemoryUsage", new MemoryUsage(-1, 202, 202, 303)));
        ClassLoadingMXBean classes = proxy(
                ClassLoadingMXBean.class, Map.of("getLoadedClassCount", -4));
        ThreadMXBean threads = proxy(ThreadMXBean.class, Map.of("getThreadCount", -3));
        GarbageCollectorMXBean unavailableGc = proxy(GarbageCollectorMXBean.class, Map.of(
                "getCollectionCount", -1L,
                "getCollectionTime", -1L));
        GarbageCollectorMXBean availableGc = proxy(GarbageCollectorMXBean.class, Map.of(
                "getCollectionCount", 5L,
                "getCollectionTime", 7L));
        List<DiagnosticEvent> events = new ArrayList<>();
        JvmRuntimeReporter reporter = new JvmRuntimeReporter(
                runtime,
                memory,
                List.of(unavailableGc, availableGc),
                classes,
                threads,
                () -> 8);

        reporter.report(event -> {
            events.add(event);
            return true;
        });

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo("jvm_runtime_summary");
            assertThat(event.attributes()).containsExactlyEntriesOf(Map.of(
                    "event.outcome", "success"));
            assertThat(event.booleanFields()).isEmpty();
            assertThat(event.doubleFields()).isEmpty();
            assertThat(event.longFields()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "jvm.uptime.ms", -25L,
                    "jvm.available.processors", 8L,
                    "jvm.memory.heap.used.bytes", 101L,
                    "jvm.memory.heap.max.bytes", -1L,
                    "jvm.memory.nonheap.used.bytes", 202L,
                    "jvm.thread.count", -3L,
                    "jvm.class.loaded.count", -4L,
                    "jvm.gc.collection.count", 5L,
                    "jvm.gc.collection.time.ms", 7L));
            assertThat(event.toString())
                    .doesNotContain("sensitive-runtime-name")
                    .doesNotContain("-Dsecret=value")
                    .doesNotContain("secret=value");
        });
        assertThat(runtimeInvocations).containsExactly("getUptime");
    }

    @Test
    void schedulesOneManagedSummaryTaskAndStopsIdempotently() {
        RecordingScheduler scheduler = new RecordingScheduler();
        List<DiagnosticEvent> events = new ArrayList<>();
        JvmPlugin plugin = new JvmPlugin();
        PluginRuntimeContext context = context(
                PluginConfig.of(Map.of("summary-interval", "2s")),
                scheduler,
                events::add);

        plugin.start(context);

        assertThat(scheduler.scheduleCalls).hasValue(1);
        assertThat(scheduler.taskName).isEqualTo("jvm-summary");
        assertThat(scheduler.initialDelay).isEqualTo(Duration.ofSeconds(2));
        assertThat(scheduler.delay).isEqualTo(Duration.ofSeconds(2));
        assertThatThrownBy(() -> plugin.start(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already started");

        scheduler.run();
        assertThat(events).singleElement()
                .extracting(DiagnosticEvent::action)
                .isEqualTo("jvm_runtime_summary");

        plugin.stop();
        plugin.stop();
        scheduler.run();

        assertThat(scheduler.task.cancelCalls).hasValue(1);
        assertThat(events).hasSize(1);
    }

    @Test
    void failedSchedulingLeavesThePluginRestartable() {
        RecordingScheduler scheduler = new RecordingScheduler();
        scheduler.failNext = true;
        JvmPlugin plugin = new JvmPlugin();
        PluginRuntimeContext context = context(PluginConfig.empty(), scheduler, event -> true);

        assertThatThrownBy(() -> plugin.start(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scheduling failed");

        plugin.start(context);
        plugin.stop();

        assertThat(scheduler.scheduleCalls).hasValue(2);
        assertThat(scheduler.task.cancelCalls).hasValue(1);
    }

    @Test
    void treatsAnUnavailableGarbageCollectorListAsEmpty() {
        RuntimeMXBean runtime = proxy(RuntimeMXBean.class, Map.of("getUptime", 1L));
        MemoryMXBean memory = proxy(MemoryMXBean.class, Map.of(
                "getHeapMemoryUsage", new MemoryUsage(-1, 2, 2, 3),
                "getNonHeapMemoryUsage", new MemoryUsage(-1, 4, 4, 5)));
        ClassLoadingMXBean classes = proxy(
                ClassLoadingMXBean.class, Map.of("getLoadedClassCount", 6));
        ThreadMXBean threads = proxy(ThreadMXBean.class, Map.of("getThreadCount", 7));
        List<DiagnosticEvent> events = new ArrayList<>();

        new JvmRuntimeReporter(runtime, memory, null, classes, threads, () -> 8)
                .report(events::add);

        assertThat(events).singleElement().satisfies(event -> assertThat(event.longFields())
                .containsEntry("jvm.gc.collection.count", 0L)
                .containsEntry("jvm.gc.collection.time.ms", 0L));
    }

    @ParameterizedTest
    @MethodSource("invalidSettings")
    void rejectsNonPositiveIntervalsAndUnknownConfiguration(String key, String value) {
        JvmPlugin plugin = new JvmPlugin();
        RecordingScheduler scheduler = new RecordingScheduler();

        assertThatThrownBy(() -> plugin.start(context(
                PluginConfig.of(Map.of(key, value)), scheduler, event -> true)))
                .isInstanceOf(PluginConfigurationException.class)
                .hasMessageContaining(key)
                .hasMessageNotContaining(value);
        assertThat(scheduler.scheduleCalls).hasValue(0);
    }

    @Test
    void passesTheBuiltInPluginContractVerifier() {
        assertThat(PluginContractVerifier.verifyProvider(
                new JvmPlugin(),
                PluginConfig.of(Map.of("summary-interval", "1s"))).valid()).isTrue();
    }

    private static Stream<Arguments> invalidSettings() {
        return Stream.of(
                Arguments.of("summary-interval", "0s"),
                Arguments.of("summary-interval", "-1s"),
                Arguments.of("private-setting", "sensitive-value"));
    }

    private static PluginRuntimeContext context(
            PluginConfig config,
            ManagedScheduler scheduler,
            EventSink events
    ) {
        return new PluginRuntimeContext() {
            @Override
            public PluginConfig config() {
                return config;
            }

            @Override
            public ManagedScheduler scheduler() {
                return scheduler;
            }

            @Override
            public ObservationChannel observations() {
                return handler -> {
                    throw new AssertionError("runtime-only plugin registered an observation handler");
                };
            }

            @Override
            public EventSink events() {
                return events;
            }

            @Override
            public System.Logger logger() {
                return System.getLogger("com.nowcoder.yierloom.plugins.jvm.test");
            }

            @Override
            public Clock clock() {
                return Clock.systemUTC();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return proxy(type, values, new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(
            Class<T> type,
            Map<String, Object> values,
            List<String> invocations
    ) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, arguments);
            }
            invocations.add(method.getName());
            if (values.containsKey(method.getName())) {
                return values.get(method.getName());
            }
            return primitiveDefault(method.getReturnType());
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object objectMethod(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "toString" -> "MXBeanFixture[" + proxy.getClass().getInterfaces()[0].getName() + "]";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            default -> throw new AssertionError("unexpected Object method: " + method);
        };
    }

    private static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0D;
        }
        throw new AssertionError("unsupported primitive: " + type);
    }

    private static final class RecordingScheduler implements ManagedScheduler {
        private final AtomicInteger scheduleCalls = new AtomicInteger();
        private String taskName;
        private Duration initialDelay;
        private Duration delay;
        private Runnable runnable;
        private RecordingTask task;
        private boolean failNext;

        @Override
        public ManagedTask scheduleWithFixedDelay(
                String taskName,
                Duration initialDelay,
                Duration delay,
                Runnable task
        ) {
            scheduleCalls.incrementAndGet();
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("scheduling failed");
            }
            this.taskName = taskName;
            this.initialDelay = initialDelay;
            this.delay = delay;
            this.runnable = task;
            this.task = new RecordingTask(taskName);
            return this.task;
        }

        private void run() {
            if (task != null && !task.isCancelled()) {
                runnable.run();
            }
        }
    }

    private static final class RecordingTask implements ManagedTask {
        private final String name;
        private final AtomicInteger cancelCalls = new AtomicInteger();
        private boolean cancelled;

        private RecordingTask(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean cancel() {
            cancelCalls.incrementAndGet();
            boolean changed = !cancelled;
            cancelled = true;
            return changed;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
