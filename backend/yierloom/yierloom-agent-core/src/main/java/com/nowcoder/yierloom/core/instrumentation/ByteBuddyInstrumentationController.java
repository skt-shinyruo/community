package com.nowcoder.yierloom.core.instrumentation;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.nowcoder.yierloom.core.plugin.ValidatedPlugin;
import com.nowcoder.yierloom.sdk.InstrumentationModule;
import com.nowcoder.yierloom.sdk.TypeInstrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

public final class ByteBuddyInstrumentationController implements PluginInstrumentationController {
    private static final System.Logger LOGGER = System.getLogger(
            ByteBuddyInstrumentationController.class.getName());

    private final Instrumentation instrumentation;
    private final AgentBuilder.InjectionStrategy injectionStrategy;
    private final HelperInjector helperInjector;
    private final TransformationErrorReporter errorReporter;
    private final Map<String, List<InstalledTransformer>> installed = new LinkedHashMap<>();
    private final Map<String, Set<String>> pluginHelpers = new LinkedHashMap<>();
    private final Map<String, String> helperOwners = new LinkedHashMap<>();

    public ByteBuddyInstrumentationController(
            Instrumentation instrumentation,
            Path injectionDirectory
    ) {
        this(instrumentation, injectionDirectory, Clock.systemUTC());
    }

    public ByteBuddyInstrumentationController(
            Instrumentation instrumentation,
            Path injectionDirectory,
            Clock clock
    ) {
        this(
                instrumentation,
                productionStrategy(instrumentation, injectionDirectory),
                new TransformationErrorReporter(
                        Objects.requireNonNull(clock, "clock"),
                        message -> LOGGER.log(System.Logger.Level.WARNING, message)));
    }

    ByteBuddyInstrumentationController(
            Instrumentation instrumentation,
            AgentBuilder.InjectionStrategy injectionStrategy,
            TransformationErrorReporter errorReporter
    ) {
        this.instrumentation = Objects.requireNonNull(instrumentation, "instrumentation");
        this.injectionStrategy = Objects.requireNonNull(injectionStrategy, "injectionStrategy");
        this.helperInjector = new HelperInjector(injectionStrategy);
        this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter");
    }

    @Override
    public synchronized void install(ValidatedPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        String pluginId = plugin.descriptor().id();
        if (installed.containsKey(pluginId)) {
            throw new IllegalStateException("instrumentation already installed for plugin: " + pluginId);
        }

        PluginPlan plan;
        try {
            plan = preflight(plugin);
        } catch (Throwable failure) {
            PluginInstrumentationException.rethrowIfFatal(failure);
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new PluginInstrumentationException(pluginId, failure);
        }
        List<InstalledTransformer> handles = new ArrayList<>();
        try {
            for (ModulePlan module : plan.modules()) {
                ResettableClassFileTransformer transformer = builderFor(pluginId, module)
                        .installOn(instrumentation);
                handles.add(new InstalledTransformer(pluginId, module.id(), transformer));
            }
            installed.put(pluginId, List.copyOf(handles));
            reserveHelpers(pluginId, plan.helperNames());
        } catch (VirtualMachineError | ThreadDeath fatal) {
            RemovalResult cleanup = remove(handles);
            retainFailedCleanup(pluginId, plan.helperNames(), cleanup.remaining());
            if (cleanup.failure() != null) {
                fatal.addSuppressed(cleanup.failure());
            }
            throw fatal;
        } catch (Throwable failure) {
            RemovalResult cleanup = remove(handles);
            retainFailedCleanup(pluginId, plan.helperNames(), cleanup.remaining());
            try {
                PluginInstrumentationException.rethrowIfFatal(failure);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                if (cleanup.failure() != null && cleanup.failure() != fatal) {
                    fatal.addSuppressed(cleanup.failure());
                }
                throw fatal;
            }
            if (cleanup.failure() != null) {
                PluginInstrumentationException.rethrowIfFatal(cleanup.failure());
                failure.addSuppressed(cleanup.failure());
            }
            throw new PluginInstrumentationException(pluginId, failure);
        }
    }

    @Override
    public synchronized void removePlugin(String pluginId) {
        Objects.requireNonNull(pluginId, "pluginId");
        List<InstalledTransformer> handles = installed.get(pluginId);
        if (handles == null) {
            return;
        }
        RemovalResult result = remove(handles);
        updateAfterRemoval(pluginId, result.remaining());
        throwRemovalFailure(pluginId, result.failure());
    }

    @Override
    public synchronized void removeAll() {
        FailureAccumulator failures = new FailureAccumulator();
        List<String> pluginIds = new ArrayList<>(installed.keySet());
        Collections.reverse(pluginIds);
        for (String pluginId : pluginIds) {
            RemovalResult result = remove(installed.get(pluginId));
            updateAfterRemoval(pluginId, result.remaining());
            failures.record(result.failure());
        }
        if (failures.failure != null) {
            PluginInstrumentationException.rethrowIfFatal(failures.failure);
            throw new PluginInstrumentationException("<multiple>", failures.failure);
        }
    }

    public synchronized List<String> installedModuleIds(String pluginId) {
        List<InstalledTransformer> handles = installed.get(pluginId);
        if (handles == null) {
            return List.of();
        }
        return handles.stream().map(InstalledTransformer::moduleId).toList();
    }

    private PluginPlan preflight(ValidatedPlugin plugin) {
        String pluginId = plugin.descriptor().id();
        Set<String> moduleIds = new HashSet<>();
        Map<String, String> declaredHelperOwners = new LinkedHashMap<>();
        List<ModuleDeclaration> declarations = new ArrayList<>();

        for (InstrumentationModule module : plugin.instrumentationModules()) {
            Objects.requireNonNull(module, "instrumentation module");
            String moduleId = Objects.requireNonNull(module.id(), "instrumentation module id");
            if (!moduleIds.add(moduleId)) {
                throw new IllegalArgumentException("duplicate instrumentation module: " + moduleId);
            }
            Set<String> helpers = new LinkedHashSet<>(Objects.requireNonNull(
                    module.helperClassNames(), "Helper class names"));
            for (String helper : helpers) {
                Objects.requireNonNull(helper, "Helper class name");
                String previousModule = declaredHelperOwners.putIfAbsent(helper, moduleId);
                if (previousModule != null) {
                    throw helperCollision(helper);
                }
                if (helperOwners.containsKey(helper)) {
                    throw helperCollision(helper);
                }
            }
            declarations.add(new ModuleDeclaration(module, moduleId, Set.copyOf(helpers)));
        }

        ClassLoader owner = Objects.requireNonNull(
                plugin.discovered().provider().getClass().getClassLoader(),
                "plugin ClassLoader");
        List<ModulePlan> modules = new ArrayList<>();
        for (ModuleDeclaration declaration : declarations) {
            List<? extends TypeInstrumentation> contributed = Objects.requireNonNull(
                    declaration.module().typeInstrumentations(), "type instrumentations");
            List<TypePlan> types = new ArrayList<>(contributed.size());
            for (TypeInstrumentation type : contributed) {
                Objects.requireNonNull(type, "type instrumentation");
                types.add(new TypePlan(
                        Objects.requireNonNull(type.typeMatcher(), "type matcher"),
                        Objects.requireNonNull(type.classLoaderMatcher(), "ClassLoader matcher"),
                        Objects.requireNonNull(type.transformer(), "transformer")));
            }
            Map<String, byte[]> helperBytes = HelperClassBytes.read(owner, declaration.helperNames());
            modules.add(new ModulePlan(
                    declaration.id(), helperBytes, List.copyOf(types)));
        }
        return new PluginPlan(List.copyOf(modules), Set.copyOf(declaredHelperOwners.keySet()));
    }

    private AgentBuilder builderFor(String pluginId, ModulePlan module) {
        AgentBuilder builder = new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.DISABLED)
                .with(injectionStrategy)
                .with(listener(pluginId, module.id()));
        for (TypePlan type : module.types()) {
            builder = builder
                    .type(type.typeMatcher(), type.classLoaderMatcher())
                    .transform(new InjectingTransformer(
                            pluginId,
                            module.id(),
                            module.helperBytes(),
                            helperInjector,
                            type.transformer(),
                            errorReporter));
        }
        return builder;
    }

    private AgentBuilder.Listener listener(String pluginId, String moduleId) {
        return new AgentBuilder.Listener.Adapter() {
            @Override
            public void onError(
                    String typeName,
                    ClassLoader classLoader,
                    JavaModule module,
                    boolean loaded,
                    Throwable failure
            ) {
                errorReporter.report(pluginId, moduleId, "byte-buddy", typeName, failure);
            }
        };
    }

    private RemovalResult remove(List<InstalledTransformer> handles) {
        List<InstalledTransformer> remaining = new ArrayList<>();
        FailureAccumulator failures = new FailureAccumulator();
        for (int index = handles.size() - 1; index >= 0; index--) {
            InstalledTransformer handle = handles.get(index);
            try {
                instrumentation.removeTransformer(handle.transformer());
            } catch (Throwable failure) {
                remaining.add(handle);
                failures.record(failure);
            }
        }
        Collections.reverse(remaining);
        return new RemovalResult(List.copyOf(remaining), failures.failure);
    }

    private void retainFailedCleanup(
            String pluginId,
            Set<String> helperNames,
            List<InstalledTransformer> remaining
    ) {
        installed.remove(pluginId);
        releaseHelpers(pluginId);
        if (remaining.isEmpty()) {
            return;
        }
        installed.put(pluginId, remaining);
        reserveHelpers(pluginId, helperNames);
    }

    private void updateAfterRemoval(String pluginId, List<InstalledTransformer> remaining) {
        if (remaining.isEmpty()) {
            installed.remove(pluginId);
            releaseHelpers(pluginId);
        } else {
            installed.put(pluginId, remaining);
        }
    }

    private void reserveHelpers(String pluginId, Set<String> helperNames) {
        Set<String> copy = Set.copyOf(helperNames);
        pluginHelpers.put(pluginId, copy);
        copy.forEach(helper -> helperOwners.put(helper, pluginId));
    }

    private void releaseHelpers(String pluginId) {
        Set<String> helpers = pluginHelpers.remove(pluginId);
        if (helpers != null) {
            helpers.forEach(helper -> helperOwners.remove(helper, pluginId));
        }
    }

    private static IllegalArgumentException helperCollision(String helperName) {
        return new IllegalArgumentException("Helper binary name is already owned: " + helperName);
    }

    private static void throwRemovalFailure(String pluginId, Throwable failure) {
        if (failure == null) {
            return;
        }
        PluginInstrumentationException.rethrowIfFatal(failure);
        throw new PluginInstrumentationException(pluginId, failure);
    }

    private static AgentBuilder.InjectionStrategy productionStrategy(
            Instrumentation instrumentation,
            Path injectionDirectory
    ) {
        Objects.requireNonNull(instrumentation, "instrumentation");
        Path directory = Objects.requireNonNull(injectionDirectory, "injectionDirectory")
                .toAbsolutePath()
                .normalize();
        try {
            Files.createDirectories(directory);
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot create Helper injection directory", failure);
        }
        if (!Files.isDirectory(directory) || !Files.isWritable(directory)) {
            throw new IllegalArgumentException("Helper injection directory is not writable");
        }
        return new AgentBuilder.InjectionStrategy.UsingInstrumentation(
                instrumentation, directory.toFile());
    }

    private record ModuleDeclaration(
            InstrumentationModule module,
            String id,
            Set<String> helperNames
    ) {
    }

    private record PluginPlan(List<ModulePlan> modules, Set<String> helperNames) {
    }

    private record ModulePlan(
            String id,
            Map<String, byte[]> helperBytes,
            List<TypePlan> types
    ) {
    }

    private record TypePlan(
            ElementMatcher<? super TypeDescription> typeMatcher,
            ElementMatcher<? super ClassLoader> classLoaderMatcher,
            AgentBuilder.Transformer transformer
    ) {
    }

    private record RemovalResult(
            List<InstalledTransformer> remaining,
            Throwable failure
    ) {
    }

    private static final class FailureAccumulator {
        private Throwable failure;

        private void record(Throwable candidate) {
            if (candidate == null) {
                return;
            }
            if (failure == null) {
                failure = candidate;
            } else {
                failure.addSuppressed(candidate);
            }
        }
    }
}
