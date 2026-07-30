package com.nowcoder.yierloom.bootstrap;

import java.lang.instrument.Instrumentation;

public final class YierLoomAgent {
    private YierLoomAgent() {
    }

    public static void premain(String agentArguments, Instrumentation instrumentation) {
        try {
            EngineLauncher.launch(agentArguments, instrumentation);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            report("[YierLoom] disabled after bootstrap failure: "
                    + failure.getClass().getName());
        }
    }

    private static void report(String message) {
        try {
            System.err.println(message);
        } catch (Throwable failure) {
            BootstrapFailures.rethrowFatal(BootstrapFailures.fatalCause(failure));
        }
    }
}
