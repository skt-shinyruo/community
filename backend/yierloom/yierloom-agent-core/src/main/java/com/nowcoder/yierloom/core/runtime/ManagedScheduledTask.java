package com.nowcoder.yierloom.core.runtime;

import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.EventSink;
import com.nowcoder.yierloom.api.ManagedTask;
import com.nowcoder.yierloom.core.FatalFailures;

final class ManagedScheduledTask implements ManagedTask, Runnable {
    private static final int FAILURE_LIMIT = 3;

    private final String name;
    private final EventSink events;
    private final Runnable delegate;
    private final Consumer<ManagedScheduledTask> cancellationListener;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
    private final Object runMonitor = new Object();
    private int running;

    ManagedScheduledTask(
            String name,
            EventSink events,
            Runnable delegate,
            Consumer<ManagedScheduledTask> cancellationListener
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.events = Objects.requireNonNull(events, "events");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.cancellationListener = Objects.requireNonNull(cancellationListener, "cancellationListener");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean cancel() {
        return cancel(false);
    }

    private boolean cancel(boolean mayInterruptIfRunning) {
        boolean firstCancellation = cancelled.compareAndSet(false, true);
        ScheduledFuture<?> scheduledFuture = future.get();
        if (scheduledFuture != null) {
            scheduledFuture.cancel(mayInterruptIfRunning);
        }
        if (firstCancellation) {
            removeWhenIdle();
        }
        return firstCancellation;
    }

    @Override
    public boolean isCancelled() {
        ScheduledFuture<?> scheduledFuture = future.get();
        return cancelled.get() || scheduledFuture != null && scheduledFuture.isCancelled();
    }

    @Override
    public void run() {
        synchronized (runMonitor) {
            if (isCancelled()) {
                return;
            }
            running++;
        }
        try {
            delegate.run();
            consecutiveFailures.set(0);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            FatalFailures.rethrow(failure);
            if (consecutiveFailures.incrementAndGet() >= FAILURE_LIMIT && cancel()) {
                events.emit(DiagnosticEvent.builder("agent_task_disabled")
                        .attribute("task.name", name)
                        .attribute("event.outcome", "failure")
                        .build());
            }
        } finally {
            synchronized (runMonitor) {
                running--;
                runMonitor.notifyAll();
            }
            removeWhenIdle();
        }
    }

    boolean cancelAndAwait(long deadline) {
        cancel(true);
        synchronized (runMonitor) {
            while (running != 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(
                            runMonitor, remaining);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    void attach(ScheduledFuture<?> scheduledFuture) {
        Objects.requireNonNull(scheduledFuture, "scheduledFuture");
        if (!future.compareAndSet(null, scheduledFuture)) {
            throw new IllegalStateException("scheduled future already attached");
        }
        if (cancelled.get()) {
            scheduledFuture.cancel(false);
        }
    }

    private void removeWhenIdle() {
        boolean removable;
        synchronized (runMonitor) {
            removable = cancelled.get() && running == 0;
        }
        if (removable) {
            cancellationListener.accept(this);
        }
    }
}
