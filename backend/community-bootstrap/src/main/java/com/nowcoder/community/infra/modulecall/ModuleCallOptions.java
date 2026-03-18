package com.nowcoder.community.infra.modulecall;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Options for unified internal calls.
 *
 * <p>Designed to keep infra module-call utilities free of logging dependencies: callers can pass a
 * {@code warnLogger} callback like {@code (msg, ex) -> log.warn(msg, ex)}.
 */
public record ModuleCallOptions<T>(
        boolean failOpen,
        Supplier<T> fallback,
        BiConsumer<String, Throwable> warnLogger
) {

    public static <T> ModuleCallOptions<T> failClosed() {
        return new ModuleCallOptions<>(false, null, null);
    }

    public static <T> ModuleCallOptions<T> failOpen(Supplier<T> fallback) {
        return new ModuleCallOptions<>(true, fallback, null);
    }

    public ModuleCallOptions<T> withWarnLogger(BiConsumer<String, Throwable> warnLogger) {
        return new ModuleCallOptions<>(this.failOpen, this.fallback, warnLogger);
    }
}
