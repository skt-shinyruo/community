package com.nowcoder.observability.runtimediagnostics.probes.method;

import com.nowcoder.observability.runtimediagnostics.core.DiagnosticRuntime;
import net.bytebuddy.asm.Advice;

public class MethodTimingAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    static long onEnter() {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    static void onExit(
            @Advice.Origin("#t") String className,
            @Advice.Origin("#m") String methodName,
            @Advice.Origin("#d") String descriptor,
            @Advice.Enter long startedAtNanos,
            @Advice.Thrown Throwable thrown
    ) {
        long durationMs = Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000L);
        DiagnosticRuntime.recordMethod(className, methodName, descriptor, durationMs);
        if (thrown != null) {
            DiagnosticRuntime.recordException(className, methodName, descriptor, thrown);
        }
    }
}
