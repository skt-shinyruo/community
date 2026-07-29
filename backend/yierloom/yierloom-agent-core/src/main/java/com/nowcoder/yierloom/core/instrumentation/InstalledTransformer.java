package com.nowcoder.yierloom.core.instrumentation;

import java.util.Objects;

import net.bytebuddy.agent.builder.ResettableClassFileTransformer;

record InstalledTransformer(
        String pluginId,
        String moduleId,
        ResettableClassFileTransformer transformer
) {
    InstalledTransformer {
        pluginId = Objects.requireNonNull(pluginId, "pluginId");
        moduleId = Objects.requireNonNull(moduleId, "moduleId");
        transformer = Objects.requireNonNull(transformer, "transformer");
    }
}
