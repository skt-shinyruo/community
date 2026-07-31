package com.example.yierloom.integration;

public final class CustomLoadedTarget {
    private static final String METHOD_HELPER =
            "com.nowcoder.yierloom.plugins.method.MethodObservationHelper";
    private static final String YIERLOOM_BRIDGE =
            "com.nowcoder.yierloom.api.YierLoomBridge";

    public String work() {
        return "custom-ok";
    }

    public String helperLoaderName() {
        return loaderName(METHOD_HELPER);
    }

    public String apiLoaderName() {
        return loaderName(YIERLOOM_BRIDGE);
    }

    private static String loaderName(String className) {
        ClassLoader targetLoader = CustomLoadedTarget.class.getClassLoader();
        try {
            ClassLoader owner = Class.forName(className, false, targetLoader).getClassLoader();
            if (owner == null) {
                return "bootstrap";
            }
            if (owner == targetLoader) {
                return "custom";
            }
            return owner == ClassLoader.getSystemClassLoader()
                    ? "system"
                    : owner.getClass().getName();
        } catch (ClassNotFoundException unavailable) {
            return "missing";
        }
    }
}
