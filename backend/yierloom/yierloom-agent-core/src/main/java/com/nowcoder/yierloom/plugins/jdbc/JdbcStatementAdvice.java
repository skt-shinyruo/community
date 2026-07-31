package com.nowcoder.yierloom.plugins.jdbc;

import net.bytebuddy.asm.Advice;

public final class JdbcStatementAdvice {
    private JdbcStatementAdvice() {
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
        JdbcObservationHelper.observe(
                arguments,
                Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L),
                thrown != null);
    }
}
