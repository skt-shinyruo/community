package com.nowcoder.community.common.trace;

public final class TraceJobRunner {

    private TraceJobRunner() {
    }

    public static void run(String jobName, Runnable action) {
        if (action == null) {
            return;
        }
        TraceContextSnapshot.currentOrNew().wrap(action).run();
    }
}
