package com.nowcoder.yierloom.core.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginConfigurationException;
import com.nowcoder.yierloom.api.PluginDescriptor;
import com.nowcoder.yierloom.api.RuntimeCapability;
import com.nowcoder.yierloom.api.YierLoomApi;
import com.nowcoder.yierloom.core.FatalFailures;
import com.nowcoder.yierloom.core.config.ApiVersion;
import com.nowcoder.yierloom.core.config.ConfigOrigin;
import com.nowcoder.yierloom.core.config.YierLoomConfig;
import com.nowcoder.yierloom.sdk.InstrumentationCapability;
import com.nowcoder.yierloom.sdk.InstrumentationModule;

public final class PluginValidator {
    private static final Pattern MODULE_ID = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Comparator<ValidatedPlugin> PLUGIN_ORDER = Comparator
            .comparingInt((ValidatedPlugin plugin) -> plugin.descriptor().order())
            .thenComparing(plugin -> plugin.descriptor().id())
            .thenComparing(plugin -> plugin.discovered().sourcePath().toString());

    public ValidationResult validate(List<DiscoveredPlugin> candidates, YierLoomConfig config) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(config, "config");
        List<Candidate> declarations = readDescriptors(candidates);

        Set<String> builtInIds = new HashSet<>();
        Map<String, Integer> builtInIdCounts = new HashMap<>();
        Map<String, Integer> externalIdCounts = new HashMap<>();
        for (Candidate candidate : declarations) {
            if (candidate.descriptor == null) {
                continue;
            }
            if (candidate.discovered.source() == PluginSource.BUILT_IN) {
                builtInIds.add(candidate.descriptor.id());
                builtInIdCounts.merge(candidate.descriptor.id(), 1, Integer::sum);
            } else {
                externalIdCounts.merge(candidate.descriptor.id(), 1, Integer::sum);
            }
        }

        for (Candidate candidate : declarations) {
            if (candidate.failed()) {
                continue;
            }
            validateDeclaration(candidate, config, builtInIds, builtInIdCounts, externalIdCounts);
        }
        rejectHelperCollisions(declarations);

        List<ValidatedPlugin> plugins = new ArrayList<>();
        List<PluginReport> reports = new ArrayList<>();
        for (Candidate candidate : declarations) {
            reports.add(candidate.report());
            if (!candidate.failed() && candidate.enabled) {
                plugins.add(new ValidatedPlugin(
                        candidate.discovered,
                        candidate.descriptor,
                        candidate.config,
                        candidate.runtimeCapability,
                        candidate.modules));
            }
        }
        plugins.sort(PLUGIN_ORDER);
        return new ValidationResult(plugins, reports);
    }

    private static List<Candidate> readDescriptors(List<DiscoveredPlugin> discoveredPlugins) {
        List<Candidate> candidates = new ArrayList<>(discoveredPlugins.size());
        for (DiscoveredPlugin discovered : discoveredPlugins) {
            Objects.requireNonNull(discovered, "discovered plugin");
            Candidate candidate = new Candidate(discovered);
            try {
                candidate.descriptor = Objects.requireNonNull(
                        discovered.provider().descriptor(), "plugin descriptor");
            } catch (Throwable failure) {
                rethrowFatal(failure);
                candidate.fail("INVALID_DESCRIPTOR");
            }
            candidates.add(candidate);
        }
        return candidates;
    }

    private static void validateDeclaration(
            Candidate candidate,
            YierLoomConfig config,
            Set<String> builtInIds,
            Map<String, Integer> builtInIdCounts,
            Map<String, Integer> externalIdCounts
    ) {
        try {
            if (!ApiVersion.isCompatible(YierLoomApi.VERSION, candidate.descriptor.apiVersion())) {
                candidate.fail("API_INCOMPATIBLE");
                return;
            }
            if (candidate.discovered.source() == PluginSource.BUILT_IN
                    && builtInIdCounts.getOrDefault(candidate.descriptor.id(), 0) > 1) {
                candidate.fail("DUPLICATE_ID");
                return;
            }
            if (candidate.discovered.source() == PluginSource.EXTERNAL
                    && builtInIds.contains(candidate.descriptor.id())) {
                candidate.fail("RESERVED_ID");
                return;
            }
            if (candidate.discovered.source() == PluginSource.EXTERNAL
                    && externalIdCounts.getOrDefault(candidate.descriptor.id(), 0) > 1) {
                candidate.fail("DUPLICATE_ID");
                return;
            }

            Object provider = candidate.discovered.provider();
            candidate.runtimeCapability = provider instanceof RuntimeCapability runtime ? runtime : null;
            candidate.instrumentationCapability = provider instanceof InstrumentationCapability instrumentation
                    ? instrumentation
                    : null;
            if (candidate.runtimeCapability == null && candidate.instrumentationCapability == null) {
                candidate.fail("INVALID_CAPABILITY");
                return;
            }

            candidate.config = config.pluginConfig(candidate.descriptor.id());
            try {
                candidate.enabled = config.pluginEnabled(candidate.descriptor);
            } catch (PluginConfigurationException invalidConfig) {
                candidate.failConfig(
                        invalidConfig.key(),
                        config.originForPluginKey(candidate.descriptor.id(), invalidConfig.key()));
                return;
            }
            if (!candidate.enabled) {
                return;
            }
            if (candidate.instrumentationCapability != null) {
                validateModules(candidate, config);
            }
            if (candidate.failed()) {
                return;
            }
            if (candidate.runtimeCapability == null && candidate.modules.isEmpty()) {
                candidate.fail("INVALID_CAPABILITY");
            }
        } catch (PluginConfigurationException invalidConfig) {
            candidate.failConfig(
                    invalidConfig.key(),
                    config.originForPluginKey(candidate.pluginId(), invalidConfig.key()));
        } catch (Throwable failure) {
            rethrowFatal(failure);
            candidate.fail("INVALID_CAPABILITY");
        }
    }

    private static void validateModules(Candidate candidate, YierLoomConfig config) {
        List<InstrumentationModule> declared;
        try {
            declared = candidate.instrumentationCapability.instrumentations(candidate.config);
        } catch (PluginConfigurationException invalidConfig) {
            candidate.failConfig(
                    invalidConfig.key(),
                    config.originForPluginKey(candidate.pluginId(), invalidConfig.key()));
            return;
        }
        if (declared == null || declared.stream().anyMatch(Objects::isNull)) {
            candidate.fail("INVALID_CAPABILITY");
            return;
        }

        Set<String> moduleIds = new HashSet<>();
        Set<String> helpers = new LinkedHashSet<>();
        List<InstrumentationModule> modules = new ArrayList<>(declared.size());
        for (InstrumentationModule module : declared) {
            String moduleId = module.id();
            if (moduleId == null
                    || !MODULE_ID.matcher(moduleId).matches()
                    || !moduleIds.add(moduleId)) {
                candidate.fail("INVALID_CAPABILITY");
                return;
            }
            Set<String> moduleHelpers = module.helperClassNames();
            if (moduleHelpers == null) {
                candidate.fail("INVALID_CAPABILITY");
                return;
            }
            for (String helper : moduleHelpers) {
                if (!isBinaryName(helper)) {
                    candidate.fail("INVALID_CAPABILITY");
                    return;
                }
                helpers.add(helper);
            }
            modules.add(module);
        }
        candidate.modules = List.copyOf(modules);
        candidate.helperNames = Set.copyOf(helpers);
    }

    private static void rejectHelperCollisions(List<Candidate> candidates) {
        Map<String, Set<Candidate>> owners = new HashMap<>();
        for (Candidate candidate : candidates) {
            if (candidate.failed() || !candidate.enabled) {
                continue;
            }
            for (String helper : candidate.helperNames) {
                owners.computeIfAbsent(
                        helper,
                        ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                        .add(candidate);
            }
        }
        Set<Candidate> colliding = Collections.newSetFromMap(new IdentityHashMap<>());
        owners.values().stream()
                .filter(ownerSet -> ownerSet.size() > 1)
                .forEach(colliding::addAll);
        colliding.forEach(candidate -> candidate.fail("HELPER_NAME_COLLISION"));
    }

    private static boolean isBinaryName(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String[] parts = value.split("\\.", -1);
        for (String part : parts) {
            if (part.isEmpty() || !Character.isJavaIdentifierStart(part.charAt(0))) {
                return false;
            }
            for (int index = 1; index < part.length(); index++) {
                if (!Character.isJavaIdentifierPart(part.charAt(index))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void rethrowFatal(Throwable failure) {
        FatalFailures.rethrow(failure);
    }

    public record ValidationResult(List<ValidatedPlugin> plugins, List<PluginReport> reports) {
        public ValidationResult {
            plugins = List.copyOf(plugins);
            reports = List.copyOf(reports);
        }
    }

    private static final class Candidate {
        private final DiscoveredPlugin discovered;
        private PluginDescriptor descriptor;
        private PluginConfig config = PluginConfig.empty();
        private RuntimeCapability runtimeCapability;
        private InstrumentationCapability instrumentationCapability;
        private List<InstrumentationModule> modules = List.of();
        private Set<String> helperNames = Set.of();
        private boolean enabled;
        private String failureReason;
        private String invalidConfigKey;
        private ConfigOrigin invalidConfigOrigin;

        private Candidate(DiscoveredPlugin discovered) {
            this.discovered = discovered;
        }

        private String pluginId() {
            return descriptor == null ? "<unknown>" : descriptor.id();
        }

        private boolean failed() {
            return failureReason != null;
        }

        private void fail(String reasonCode) {
            if (failureReason == null) {
                failureReason = reasonCode;
            }
        }

        private void failConfig(String key, ConfigOrigin origin) {
            if (failureReason == null) {
                failureReason = "INVALID_CONFIG";
                invalidConfigKey = key;
                invalidConfigOrigin = origin;
            }
        }

        private PluginReport report() {
            if (!failed()) {
                return PluginReport.validated(discovered, pluginId(), enabled);
            }
            if ("INVALID_CONFIG".equals(failureReason)) {
                return PluginReport.invalidConfig(
                        discovered,
                        pluginId(),
                        enabled,
                        invalidConfigKey,
                        invalidConfigOrigin);
            }
            return PluginReport.failed(discovered, pluginId(), enabled, failureReason);
        }
    }
}
