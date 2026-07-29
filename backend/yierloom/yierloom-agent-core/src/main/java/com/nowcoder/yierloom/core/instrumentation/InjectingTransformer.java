package com.nowcoder.yierloom.core.instrumentation;

import java.security.ProtectionDomain;
import java.util.Map;
import java.util.Objects;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

final class InjectingTransformer implements AgentBuilder.Transformer {
    private final String pluginId;
    private final String moduleId;
    private final Map<String, byte[]> helperBytes;
    private final HelperInjector helperInjector;
    private final AgentBuilder.Transformer delegate;
    private final TransformationErrorReporter errorReporter;

    InjectingTransformer(
            String pluginId,
            String moduleId,
            Map<String, byte[]> helperBytes,
            HelperInjector helperInjector,
            AgentBuilder.Transformer delegate,
            TransformationErrorReporter errorReporter
    ) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId");
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.helperBytes = Objects.requireNonNull(helperBytes, "helperBytes");
        this.helperInjector = Objects.requireNonNull(helperInjector, "helperInjector");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter");
    }

    @Override
    public DynamicType.Builder<?> transform(
            DynamicType.Builder<?> builder,
            TypeDescription type,
            ClassLoader loader,
            JavaModule module,
            ProtectionDomain protectionDomain
    ) {
        try {
            helperInjector.inject(loader, protectionDomain, helperBytes);
        } catch (Throwable failure) {
            PluginInstrumentationException.rethrowIfFatal(failure);
            errorReporter.report(
                    pluginId, moduleId, "helper-injection", type.getName(), failure);
            return builder;
        }
        try {
            DynamicType.Builder<?> transformed = delegate.transform(
                    builder, type, loader, module, protectionDomain);
            if (transformed == null) {
                throw new IllegalStateException("plugin transformer returned null");
            }
            return transformed;
        } catch (Throwable failure) {
            PluginInstrumentationException.rethrowIfFatal(failure);
            errorReporter.report(pluginId, moduleId, "transformer", type.getName(), failure);
            return builder;
        }
    }
}
