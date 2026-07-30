package com.nowcoder.yierloom.bootstrap;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class BootstrapResourceLease implements Runnable {
    private final Runnable cleanup;
    private final AtomicBoolean ownershipAccepted = new AtomicBoolean();
    private final AtomicBoolean cleanupRequested = new AtomicBoolean();
    private final AtomicBoolean launchCompleted = new AtomicBoolean();
    private final AtomicBoolean retryHookRegistered = new AtomicBoolean();
    private final AtomicReference<Thread> retryHook = new AtomicReference<>();

    BootstrapResourceLease(Runnable cleanup) {
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
    }

    public void acceptOwnership() {
        ownershipAccepted.set(true);
    }

    public void releaseOwnership() {
        ownershipAccepted.set(false);
    }

    boolean ownershipAccepted() {
        return ownershipAccepted.get();
    }

    @Override
    public void run() {
        cleanupRequested.set(true);
    }

    public void installShutdownHook(Runnable coreShutdown) {
        Objects.requireNonNull(coreShutdown, "coreShutdown");
        Thread shutdown = new Thread(
                () -> runAtShutdown(coreShutdown),
                "yierloom-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdown);
    }

    void completeLaunch() {
        if (launchCompleted.compareAndSet(false, true) && cleanupRequested.get()) {
            cleanupNow();
        }
    }

    synchronized void cleanupNow() {
        try {
            cleanup.run();
        } catch (Throwable failure) {
            Throwable registrationFailure = registerRetryHook();
            throwFailure(BootstrapFailures.preferred(failure, registrationFailure));
        }
        throwFailure(unregisterRetryHook());
    }

    void runAtShutdown(Runnable coreShutdown) {
        Throwable coreFailure = BootstrapFailures.capture(coreShutdown);
        Throwable cleanupFailure = cleanupRequested.get()
                ? cleanupAtShutdown()
                : null;
        throwFailure(BootstrapFailures.preferred(coreFailure, cleanupFailure));
    }

    private Throwable cleanupAtShutdown() {
        Throwable first = BootstrapFailures.capture(cleanup);
        if (first == null) {
            return null;
        }
        Throwable fatal = BootstrapFailures.fatalCause(first);
        Throwable retry = BootstrapFailures.capture(cleanup);
        if (retry == null) {
            return fatal;
        }
        return BootstrapFailures.preferred(first, retry);
    }

    private Throwable registerRetryHook() {
        if (!retryHookRegistered.compareAndSet(false, true)) {
            return null;
        }
        try {
            Thread retry = new Thread(this::retryAtShutdown, "yierloom-resource-cleanup");
            retry.setDaemon(true);
            retryHook.set(retry);
            Runtime.getRuntime().addShutdownHook(retry);
            return null;
        } catch (Throwable failure) {
            retryHook.set(null);
            retryHookRegistered.set(false);
            return failure;
        }
    }

    private Throwable unregisterRetryHook() {
        Thread retry = retryHook.get();
        if (retry == null) {
            return null;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(retry);
            retryHook.compareAndSet(retry, null);
            retryHookRegistered.set(false);
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private void retryAtShutdown() {
        BootstrapFailures.rethrowFatal(cleanupAtShutdown());
    }

    boolean retryHookRegistered() {
        return retryHookRegistered.get();
    }

    boolean cleanupRequested() {
        return cleanupRequested.get();
    }

    private static void throwFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("YierLoom bootstrap cleanup failed", failure);
    }
}
