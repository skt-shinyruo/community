package com.example.yierloomfixture;

public final class YierLoomPluginProbe {
    private YierLoomPluginProbe() {
    }

    public static void main(String[] arguments) throws Exception {
        ObservedTarget target = new ObservedTarget();
        target.slowCall();

        IllegalStateException expected = new IllegalStateException(
                "private-exception-message");
        try {
            target.fail(expected);
        } catch (IllegalStateException actual) {
            System.out.println("same-exception=" + (actual == expected));
        }

        Thread.sleep(200);
        System.out.println("probe-finished=true");
    }
}

final class ObservedTarget {
    void slowCall() throws InterruptedException {
        Thread.sleep(5);
    }

    void fail(IllegalStateException failure) {
        throw failure;
    }
}
