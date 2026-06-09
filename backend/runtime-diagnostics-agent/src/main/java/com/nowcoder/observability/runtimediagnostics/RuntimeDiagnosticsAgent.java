package com.nowcoder.observability.runtimediagnostics;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;
import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfigLoader;
import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEventLogger;
import com.nowcoder.observability.runtimediagnostics.core.DiagnosticRuntime;
import com.nowcoder.observability.runtimediagnostics.core.Probe;
import com.nowcoder.observability.runtimediagnostics.core.ProbeContext;
import com.nowcoder.observability.runtimediagnostics.core.ProbeRegistry;
import com.nowcoder.observability.runtimediagnostics.match.DiagnosticsMatcher;
import com.nowcoder.observability.runtimediagnostics.probes.exception.ExceptionDiagnosticsProbe;
import com.nowcoder.observability.runtimediagnostics.probes.jvm.JvmDiagnosticsProbe;
import com.nowcoder.observability.runtimediagnostics.probes.dependency.DependencyDiagnosticsRuntime;
import com.nowcoder.observability.runtimediagnostics.probes.http.HttpDiagnosticsProbe;
import com.nowcoder.observability.runtimediagnostics.probes.http.HttpExchangeAdvice;
import com.nowcoder.observability.runtimediagnostics.probes.jdbc.JdbcDiagnosticsProbe;
import com.nowcoder.observability.runtimediagnostics.probes.jdbc.JdbcStatementAdvice;
import com.nowcoder.observability.runtimediagnostics.probes.kafka.KafkaDiagnosticsProbe;
import com.nowcoder.observability.runtimediagnostics.probes.kafka.KafkaTemplateAdvice;
import com.nowcoder.observability.runtimediagnostics.probes.method.MethodDiagnosticsProbe;
import com.nowcoder.observability.runtimediagnostics.probes.method.MethodLatencyAggregator;
import com.nowcoder.observability.runtimediagnostics.probes.method.MethodTimingAdvice;
import com.nowcoder.observability.runtimediagnostics.probes.redis.RedisDiagnosticsProbe;
import com.nowcoder.observability.runtimediagnostics.probes.redis.RedisTemplateAdvice;
import com.nowcoder.observability.runtimediagnostics.probes.thread.ThreadDiagnosticsProbe;
import com.nowcoder.observability.runtimediagnostics.trace.TraceContextReader;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.lang.instrument.Instrumentation;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isBridge;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isNative;
import static net.bytebuddy.matcher.ElementMatchers.isSynthetic;
import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public final class RuntimeDiagnosticsAgent {

    private static volatile StartupHooks startupHooks = new StartupHooks();

    private RuntimeDiagnosticsAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        try {
            install(agentArgs, instrumentation);
        } catch (Throwable ex) {
            System.err.println("[runtime-diagnostics-agent] disabled after startup failure: " + ex);
        }
    }

    static void install(String agentArgs, Instrumentation instrumentation) {
        DiagnosticsConfig config = DiagnosticsConfigLoader.load(agentArgs);
        if (!config.enabled() || instrumentation == null) {
            return;
        }
        DiagnosticsMatcher matcher = new DiagnosticsMatcher(config);
        MethodLatencyAggregator methodAggregator = new MethodLatencyAggregator(config.maxTrackedKeys());
        DiagnosticEventLogger logger = new DiagnosticEventLogger();
        TraceContextReader traceReader = new TraceContextReader();
        StartupHooks hooks = startupHooks;
        List<Probe> probes = hooks.probes(methodAggregator);

        AgentBuilder agentBuilder = new AgentBuilder.Default()
                .ignore(new ElementMatcher.Junction.AbstractBase<>() {
                    @Override
                    public boolean matches(TypeDescription target) {
                        return !matcher.shouldInstrumentClass(target.getName())
                                && !dependencyTarget(config, target);
                    }
                })
                .type(new ElementMatcher.Junction.AbstractBase<>() {
                    @Override
                    public boolean matches(TypeDescription target) {
                        return matcher.shouldInstrumentClass(target.getName());
                    }
                })
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.visit(Advice.to(MethodTimingAdvice.class).on(not(isConstructor())
                                .and(not(isTypeInitializer()))
                                .and(not(isAbstract()))
                                .and(not(isNative()))
                                .and(not(isBridge()))
                                .and(not(isSynthetic())))));
        if (config.probeEnabled("jdbc")) {
            agentBuilder = agentBuilder
                    .type(jdbcStatementTypes())
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                            builder.visit(Advice.to(JdbcStatementAdvice.class).on(jdbcStatementMethods())));
        }
        if (config.probeEnabled("http")) {
            agentBuilder = agentBuilder
                    .type(httpExchangeFunctionTypes())
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                            builder.visit(Advice.to(HttpExchangeAdvice.class).on(named("exchange"))));
        }
        if (config.probeEnabled("redis")) {
            agentBuilder = agentBuilder
                    .type(redisTemplateTypes())
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                            builder.visit(Advice.to(RedisTemplateAdvice.class).on(redisTemplateMethods())));
        }
        if (config.probeEnabled("kafka")) {
            KafkaTemplateAdvice.configure(config);
            agentBuilder = agentBuilder
                    .type(kafkaTemplateTypes())
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                            builder.visit(Advice.to(KafkaTemplateAdvice.class).on(named("send"))));
        }
        ResettableClassFileTransformer transformer = agentBuilder.installOn(instrumentation);
        ProbeRegistry registry = new ProbeRegistry(probes);

        try {
            DiagnosticRuntime.initialize(config, methodAggregator, logger);
            DependencyDiagnosticsRuntime.initialize(config, logger);
            hooks.afterRuntimeInitialized();
            ProbeContext context = new ProbeContext(config, logger, traceReader);
            registry.startEnabled(config, context);
            hooks.afterProbesStarted();
        } catch (Throwable ex) {
            registry.stopStarted();
            DiagnosticRuntime.disable();
            DependencyDiagnosticsRuntime.resetForTests();
            try {
                transformer.reset(instrumentation, AgentBuilder.RedefinitionStrategy.DISABLED);
            } catch (Throwable ignored) {
            }
            throw ex;
        }
    }

    static void replaceStartupHooksForTests(StartupHooks replacement) {
        startupHooks = replacement == null ? new StartupHooks() : replacement;
    }

    static class StartupHooks {
        List<Probe> probes(MethodLatencyAggregator methodAggregator) {
            return List.of(
                    new MethodDiagnosticsProbe(methodAggregator),
                    new ExceptionDiagnosticsProbe(),
                    new HttpDiagnosticsProbe(),
                    new JdbcDiagnosticsProbe(),
                    new RedisDiagnosticsProbe(),
                    new KafkaDiagnosticsProbe(),
                    new ThreadDiagnosticsProbe(),
                    new JvmDiagnosticsProbe()
            );
        }

        void afterRuntimeInitialized() {
        }

        void afterProbesStarted() {
        }
    }

    private static boolean dependencyTarget(DiagnosticsConfig config, TypeDescription target) {
        return (config.probeEnabled("jdbc") && jdbcStatementTypes().matches(target))
                || (config.probeEnabled("http") && httpExchangeFunctionTypes().matches(target))
                || (config.probeEnabled("redis") && redisTemplateTypes().matches(target))
                || (config.probeEnabled("kafka") && kafkaTemplateTypes().matches(target));
    }

    private static ElementMatcher.Junction<TypeDescription> httpExchangeFunctionTypes() {
        return hasSuperType(named("org.springframework.web.reactive.function.client.ExchangeFunction"));
    }

    private static ElementMatcher.Junction<TypeDescription> redisTemplateTypes() {
        return named("org.springframework.data.redis.core.RedisTemplate");
    }

    private static ElementMatcher.Junction<net.bytebuddy.description.method.MethodDescription> redisTemplateMethods() {
        return named("execute")
                .or(named("executePipelined"))
                .or(named("executeWithStickyConnection"));
    }

    private static ElementMatcher.Junction<TypeDescription> kafkaTemplateTypes() {
        return named("org.springframework.kafka.core.KafkaTemplate");
    }

    private static ElementMatcher.Junction<TypeDescription> jdbcStatementTypes() {
        return not(nameStartsWith("java."))
                .and(not(nameStartsWith("javax.")))
                .and(not(nameStartsWith("sun.")))
                .and(hasSuperType(named("java.sql.Statement")));
    }

    private static ElementMatcher.Junction<net.bytebuddy.description.method.MethodDescription> jdbcStatementMethods() {
        return named("execute")
                .or(named("executeQuery"))
                .or(named("executeUpdate"))
                .or(named("executeLargeUpdate"))
                .or(named("executeBatch"));
    }
}
