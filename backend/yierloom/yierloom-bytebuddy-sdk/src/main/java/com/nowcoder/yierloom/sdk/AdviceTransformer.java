package com.nowcoder.yierloom.sdk;

import java.security.ProtectionDomain;
import java.util.List;
import java.util.Objects;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

public final class AdviceTransformer implements AgentBuilder.Transformer {
    private final Class<?> adviceClass;
    private final ElementMatcher<? super MethodDescription> methodMatcher;
    private final List<AdviceBinding> bindings;
    private final AgentBuilder.Transformer delegate;

    AdviceTransformer(
            Class<?> adviceClass,
            ElementMatcher<? super MethodDescription> methodMatcher,
            List<AdviceBinding> bindings
    ) {
        this.adviceClass = Objects.requireNonNull(adviceClass);
        this.methodMatcher = Objects.requireNonNull(methodMatcher);
        this.bindings = List.copyOf(bindings);
        Advice.WithCustomMapping mapping = Advice.withCustomMapping();
        for (AdviceBinding binding : this.bindings) {
            mapping = mapping.bind(binding.annotationType(), binding.value());
        }
        this.delegate = new AgentBuilder.Transformer.ForAdvice(mapping)
                .include(adviceClass.getClassLoader())
                .advice(methodMatcher, adviceClass.getName());
    }

    public Class<?> adviceClass() {
        return adviceClass;
    }

    public ElementMatcher<? super MethodDescription> methodMatcher() {
        return methodMatcher;
    }

    public List<AdviceBinding> bindings() {
        return bindings;
    }

    @Override
    public DynamicType.Builder<?> transform(
            DynamicType.Builder<?> builder,
            TypeDescription type,
            ClassLoader loader,
            JavaModule module,
            ProtectionDomain protectionDomain
    ) {
        return delegate.transform(builder, type, loader, module, protectionDomain);
    }
}
