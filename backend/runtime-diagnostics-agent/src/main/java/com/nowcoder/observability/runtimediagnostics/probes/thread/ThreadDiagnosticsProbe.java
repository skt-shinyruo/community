package com.nowcoder.observability.runtimediagnostics.probes.thread;

import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEvent;
import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEventLogger;
import com.nowcoder.observability.runtimediagnostics.core.Probe;
import com.nowcoder.observability.runtimediagnostics.core.ProbeContext;
import com.nowcoder.observability.runtimediagnostics.core.ScheduledProbeSupport;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.EnumMap;
import java.util.Map;

public class ThreadDiagnosticsProbe implements Probe {

    private final ThreadMXBean threadMxBean;
    private volatile Thread thread;

    public ThreadDiagnosticsProbe() {
        this(ManagementFactory.getThreadMXBean());
    }

    public ThreadDiagnosticsProbe(ThreadMXBean threadMxBean) {
        this.threadMxBean = threadMxBean;
    }

    @Override
    public String name() {
        return "thread";
    }

    @Override
    public void start(ProbeContext context) {
        thread = ScheduledProbeSupport.startDaemon(
                "runtime-diagnostics-thread-snapshot",
                context.config().threadSnapshotInterval(),
                () -> reportOnce(context.logger())
        );
    }

    @Override
    public void stop() {
        Thread current = thread;
        if (current != null) {
            current.interrupt();
        }
        thread = null;
    }

    public void reportOnce(DiagnosticEventLogger logger) {
        ThreadInfo[] threadInfos = threadMxBean.dumpAllThreads(false, false);
        Map<Thread.State, Integer> stateCounts = new EnumMap<>(Thread.State.class);
        int lockWaitCount = 0;
        for (ThreadInfo threadInfo : threadInfos) {
            if (threadInfo == null || threadInfo.getThreadState() == null) {
                continue;
            }
            Thread.State state = threadInfo.getThreadState();
            stateCounts.merge(state, 1, Integer::sum);
            if (isLockWait(threadInfo, state)) {
                lockWaitCount++;
            }
        }

        long[] deadlocked = threadMxBean.findDeadlockedThreads();
        DiagnosticEvent event = DiagnosticEvent.builder("thread_snapshot", "snapshot", "thread")
                .put("thread.count", threadInfos.length)
                .put("thread.state.runnable", stateCounts.getOrDefault(Thread.State.RUNNABLE, 0))
                .put("thread.state.blocked", stateCounts.getOrDefault(Thread.State.BLOCKED, 0))
                .put("thread.state.waiting", stateCounts.getOrDefault(Thread.State.WAITING, 0))
                .put("thread.state.timed_waiting", stateCounts.getOrDefault(Thread.State.TIMED_WAITING, 0))
                .put("thread.deadlock.count", deadlocked == null ? 0 : deadlocked.length)
                .put("thread.lock.wait.count", lockWaitCount)
                .build();
        logger.log(event);
    }

    private boolean isLockWait(ThreadInfo threadInfo, Thread.State state) {
        if (state == Thread.State.BLOCKED) {
            return true;
        }
        return threadInfo.getLockName() != null
                && (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING);
    }
}
