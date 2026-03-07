package com.nowcoder.community.infra.internalclient;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Options for unified internal calls.
 *
 * <p>Designed to keep {@code infra-internal-client} free of logging dependencies: callers can pass a
 * {@code warnLogger} callback like {@code (msg, ex) -> log.warn(msg, ex)}.
 */
public record InternalCallOptions<T>(
        boolean failOpen,
        Supplier<T> fallback,
        BiConsumer<String, Throwable> warnLogger
) {

    public static <T> InternalCallOptions<T> failClosed() {
        return new InternalCallOptions<>(false, null, null);
    }

    public static <T> InternalCallOptions<T> failOpen(Supplier<T> fallback) {
        return new InternalCallOptions<>(true, fallback, null);
    }

    public InternalCallOptions<T> withWarnLogger(BiConsumer<String, Throwable> warnLogger) {
        return new InternalCallOptions<>(this.failOpen, this.fallback, warnLogger);
    }
}

