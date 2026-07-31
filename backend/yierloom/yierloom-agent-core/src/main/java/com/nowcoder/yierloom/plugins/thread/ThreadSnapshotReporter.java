package com.nowcoder.yierloom.plugins.thread;

import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.EventSink;

public final class ThreadSnapshotReporter {
    private final SnapshotSource source;

    public ThreadSnapshotReporter(ThreadMXBean threadMxBean) {
        Objects.requireNonNull(threadMxBean, "threadMxBean");
        this.source = () -> readSnapshot(threadMxBean);
    }

    ThreadSnapshotReporter(SnapshotSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public void report(EventSink events) {
        Objects.requireNonNull(events, "events");
        Snapshot snapshot = Objects.requireNonNull(source.snapshot(), "snapshot");
        Map<Thread.State, Long> stateCounts = new EnumMap<>(Thread.State.class);
        long lockWaitCount = 0;
        for (ThreadSample sample : snapshot.samples()) {
            if (sample == null || sample.state() == null) {
                continue;
            }
            Thread.State state = sample.state();
            stateCounts.merge(state, 1L, Long::sum);
            if (isLockWait(sample, state)) {
                lockWaitCount++;
            }
        }

        events.emit(DiagnosticEvent.builder("thread_snapshot")
                .attribute("event.outcome", "snapshot")
                .longField("thread.count", snapshot.threadCount())
                .longField("thread.state.runnable", count(stateCounts, Thread.State.RUNNABLE))
                .longField("thread.state.blocked", count(stateCounts, Thread.State.BLOCKED))
                .longField("thread.state.waiting", count(stateCounts, Thread.State.WAITING))
                .longField(
                        "thread.state.timed_waiting",
                        count(stateCounts, Thread.State.TIMED_WAITING))
                .longField("thread.deadlock.count", snapshot.deadlockCount())
                .longField("thread.lock.wait.count", lockWaitCount)
                .build());
    }

    private static Snapshot readSnapshot(ThreadMXBean threadMxBean) {
        ThreadInfo[] threadInfos = threadMxBean.dumpAllThreads(false, false);
        List<ThreadSample> samples = new ArrayList<>(threadInfos.length);
        for (ThreadInfo threadInfo : threadInfos) {
            if (threadInfo == null) {
                continue;
            }
            samples.add(new ThreadSample(
                    threadInfo.getThreadState(),
                    threadInfo.getLockName() != null));
        }
        long[] deadlockedThreads = threadMxBean.findDeadlockedThreads();
        return new Snapshot(
                threadInfos.length,
                samples,
                deadlockedThreads == null ? 0 : deadlockedThreads.length);
    }

    private static long count(Map<Thread.State, Long> counts, Thread.State state) {
        return counts.getOrDefault(state, 0L);
    }

    private static boolean isLockWait(ThreadSample sample, Thread.State state) {
        boolean waitingState = state == Thread.State.WAITING
                || state == Thread.State.TIMED_WAITING;
        return state == Thread.State.BLOCKED || (waitingState && sample.waitingOnLock());
    }

    @FunctionalInterface
    interface SnapshotSource {
        Snapshot snapshot();
    }

    record Snapshot(long threadCount, List<ThreadSample> samples, long deadlockCount) {
        Snapshot {
            Objects.requireNonNull(samples, "samples");
            samples = List.copyOf(samples);
        }
    }

    record ThreadSample(Thread.State state, boolean waitingOnLock) {
    }
}
