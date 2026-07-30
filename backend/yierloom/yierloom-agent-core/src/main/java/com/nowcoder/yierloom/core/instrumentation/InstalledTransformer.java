package com.nowcoder.yierloom.core.instrumentation;

import java.util.Objects;

final class InstalledTransformer {
    private final String pluginId;
    private final String moduleId;
    private final QuiescingTransformer transformer;
    private boolean detached;

    InstalledTransformer(String pluginId, String moduleId, QuiescingTransformer transformer) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId");
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.transformer = Objects.requireNonNull(transformer, "transformer");
    }

    String moduleId() {
        return moduleId;
    }

    QuiescingTransformer transformer() {
        return transformer;
    }

    synchronized boolean remove(java.lang.instrument.Instrumentation instrumentation, long deadline) {
        transformer.closeAdmission();
        if (!detached) {
            if (!instrumentation.removeTransformer(transformer)) {
                return false;
            }
            detached = true;
        }
        return transformer.awaitQuiescence(deadline);
    }
}
