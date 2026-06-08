package com.nowcoder.observability.methodprofiler.instrument;

import net.bytebuddy.asm.Advice;

public class ProfilingAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    static long onEnter() {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    static void onExit(
            @Advice.Origin("#t") String className,
            @Advice.Origin("#m") String methodName,
            @Advice.Origin("#d") String descriptor,
            @Advice.Enter long startedAtNanos
    ) {
        long durationMs = Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000L);
        ProfilerRuntime.record(className, methodName, descriptor, durationMs);
    }
}
