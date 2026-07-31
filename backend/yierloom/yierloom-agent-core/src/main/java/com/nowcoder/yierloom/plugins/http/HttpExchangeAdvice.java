package com.nowcoder.yierloom.plugins.http;

import net.bytebuddy.asm.Advice;

public final class HttpExchangeAdvice {
    private HttpExchangeAdvice() {
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter() {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(
            @Advice.AllArguments Object[] arguments,
            @Advice.Enter long startedAt,
            @Advice.Thrown Throwable thrown
    ) {
        HttpObservationHelper.observe(
                arguments,
                Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L),
                thrown != null);
    }
}
