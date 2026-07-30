package com.nowcoder.yierloom.plugins.method;

import net.bytebuddy.asm.Advice;

public final class MethodAdvice {
    private MethodAdvice() {
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter() {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(
            @Advice.Origin("#t") String className,
            @Advice.Origin("#m") String methodName,
            @Advice.Origin("#d") String descriptor,
            @Advice.Enter long startedAt
    ) {
        MethodObservationHelper.observe(
                className,
                methodName,
                descriptor,
                Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L));
    }
}
