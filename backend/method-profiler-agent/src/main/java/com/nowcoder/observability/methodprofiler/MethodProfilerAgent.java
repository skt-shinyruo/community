package com.nowcoder.observability.methodprofiler;

import com.nowcoder.observability.methodprofiler.config.ProfilerConfig;
import com.nowcoder.observability.methodprofiler.config.ProfilerConfigLoader;
import com.nowcoder.observability.methodprofiler.instrument.ProfilerRuntime;
import com.nowcoder.observability.methodprofiler.instrument.ProfilingAdvice;
import com.nowcoder.observability.methodprofiler.log.ProfilerEventLogger;
import com.nowcoder.observability.methodprofiler.match.MethodProfilerMatcher;
import com.nowcoder.observability.methodprofiler.schedule.SummaryReporter;
import com.nowcoder.observability.methodprofiler.stats.MethodLatencyAggregator;
import com.nowcoder.observability.methodprofiler.trace.TraceContextReader;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isBridge;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isNative;
import static net.bytebuddy.matcher.ElementMatchers.isSynthetic;
import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.not;

public final class MethodProfilerAgent {

    private MethodProfilerAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        try {
            install(agentArgs, instrumentation);
        } catch (Throwable ex) {
            System.err.println("[method-profiler-agent] disabled after startup failure: "
                    + ex.getClass().getName() + ": " + String.valueOf(ex.getMessage()));
        }
    }

    private static void install(String agentArgs, Instrumentation instrumentation) {
        ProfilerConfig config = ProfilerConfigLoader.load(agentArgs);
        if (!config.enabled() || instrumentation == null) {
            return;
        }
        MethodProfilerMatcher matcher = new MethodProfilerMatcher(config);
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(config.maxTrackedMethods());
        ProfilerRuntime.initialize(config, aggregator);
        new SummaryReporter(aggregator, new ProfilerEventLogger(), new TraceContextReader(), config.topN())
                .start(config.summaryInterval());

        new AgentBuilder.Default()
                .ignore(new ElementMatcher.Junction.AbstractBase<>() {
                    @Override
                    public boolean matches(TypeDescription target) {
                        return !matcher.shouldInstrumentClass(target.getName());
                    }
                })
                .type(new ElementMatcher.Junction.AbstractBase<>() {
                    @Override
                    public boolean matches(TypeDescription target) {
                        return matcher.shouldInstrumentClass(target.getName());
                    }
                })
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.visit(Advice.to(ProfilingAdvice.class).on(not(isConstructor())
                                .and(not(isTypeInitializer()))
                                .and(not(isAbstract()))
                                .and(not(isNative()))
                                .and(not(isBridge()))
                                .and(not(isSynthetic())))))
                .installOn(instrumentation);
    }
}
