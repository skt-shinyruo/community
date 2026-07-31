package com.nowcoder.yierloom.plugins.redis;

import net.bytebuddy.asm.Advice;

public final class RedisTemplateAdvice {
    private RedisTemplateAdvice() {
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter() {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(
            @Advice.Origin("#m") String methodName,
            @Advice.AllArguments Object[] arguments,
            @Advice.Enter long startedAt,
            @Advice.Thrown Throwable thrown
    ) {
        RedisObservationHelper.observe(
                methodName,
                arguments,
                Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L),
                thrown != null);
    }
}
