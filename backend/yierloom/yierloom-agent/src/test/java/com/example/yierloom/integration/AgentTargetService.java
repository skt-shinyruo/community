package com.example.yierloom.integration;

public final class AgentTargetService {
    private static final String METHOD_HELPER =
            "com.nowcoder.yierloom.plugins.method.MethodObservationHelper";
    private static final String YIERLOOM_BRIDGE =
            "com.nowcoder.yierloom.api.YierLoomBridge";
    private final IllegalStateException targetBoom =
            new IllegalStateException("target-boom");

    public String fast() {
        return "fast-ok";
    }

    public String slow() throws InterruptedException {
        Thread.sleep(10);
        return "slow-ok";
    }

    public void throwsTargetBoom() {
        throw targetBoom;
    }

    public IllegalStateException targetBoom() {
        return targetBoom;
    }

    public String helperLoaderName() {
        return loaderName(METHOD_HELPER);
    }

    public String apiLoaderName() {
        return loaderName(YIERLOOM_BRIDGE);
    }

    private static String loaderName(String className) {
        try {
            ClassLoader owner = Class.forName(
                    className, false, AgentTargetService.class.getClassLoader()).getClassLoader();
            if (owner == null) {
                return "bootstrap";
            }
            return owner == ClassLoader.getSystemClassLoader()
                    ? "system"
                    : owner.getClass().getName();
        } catch (ClassNotFoundException unavailable) {
            return "missing";
        }
    }
}
