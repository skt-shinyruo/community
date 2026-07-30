package com.nowcoder.yierloom.plugins.exception;

import net.bytebuddy.asm.Advice;

public final class ExceptionAdvice {
    private ExceptionAdvice() {
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(
            @Advice.Origin("#t") String className,
            @Advice.Origin("#m") String methodName,
            @Advice.Origin("#d") String descriptor,
            @Advice.Thrown Throwable throwable
    ) {
        if (throwable != null) {
            ExceptionObservationHelper.observe(
                    className, methodName, descriptor, throwable);
        }
    }
}
