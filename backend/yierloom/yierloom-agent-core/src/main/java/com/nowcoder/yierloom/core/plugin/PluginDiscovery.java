package com.nowcoder.yierloom.core.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.nowcoder.yierloom.api.YierLoomPlugin;
import com.nowcoder.yierloom.core.config.YierLoomConfig;

public final class PluginDiscovery {
    private static final String SERVICE_ENTRY =
            "META-INF/services/" + YierLoomPlugin.class.getName();
    private static final List<String> FORBIDDEN_CLASS_ENTRIES = List.of(
            "com/nowcoder/yierloom/api/",
            "com/nowcoder/yierloom/sdk/",
            "net/bytebuddy/",
            "com/nowcoder/yierloom/core/",
            "com/nowcoder/yierloom/bootstrap/",
            "com/nowcoder/yierloom/plugins/");

    public Result discover(YierLoomConfig config, ClassLoader engineLoader) {
        Objects.requireNonNull(config, "config");
        Result builtIns = discoverBuiltIns(engineLoader);
        List<DiscoveredPlugin> plugins = new ArrayList<>(builtIns.plugins());
        List<PluginIssue> issues = new ArrayList<>(builtIns.issues());
        List<YierLoomPluginClassLoader> externalLoaders = new ArrayList<>();

        if (config.pluginDirectory().isEmpty()) {
            return new Result(plugins, issues, externalLoaders);
        }
        Path directory = config.pluginDirectory().orElseThrow().toAbsolutePath().normalize();
        if (!Files.isDirectory(directory) || !Files.isReadable(directory)) {
            issues.add(new PluginIssue(directory, "EXTERNAL_DIRECTORY_INVALID"));
            return new Result(plugins, issues, externalLoaders);
        }

        List<Path> candidates;
        try (var files = Files.list(directory)) {
            candidates = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted()
                    .toList();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            issues.add(new PluginIssue(directory, "EXTERNAL_DIRECTORY_INVALID"));
            return new Result(plugins, issues, externalLoaders);
        }

        for (Path candidate : candidates) {
            discoverExternal(candidate, engineLoader, plugins, issues, externalLoaders);
        }
        return new Result(plugins, issues, externalLoaders);
    }

    public Result discoverBuiltIns(ClassLoader engineLoader) {
        Objects.requireNonNull(engineLoader, "engineLoader");
        List<DiscoveredPlugin> plugins = new ArrayList<>();
        List<PluginIssue> issues = new ArrayList<>();
        Iterator<YierLoomPlugin> providers = ServiceLoader
                .load(YierLoomPlugin.class, engineLoader)
                .iterator();
        int consecutiveEnumerationFailures = 0;
        while (consecutiveEnumerationFailures < 256) {
            boolean hasNext;
            try {
                hasNext = providers.hasNext();
            } catch (Throwable failure) {
                rethrowFatal(failure);
                issues.add(new PluginIssue(Path.of("built-in"), "PROVIDER_DISCOVERY_FAILED"));
                consecutiveEnumerationFailures++;
                continue;
            }
            if (!hasNext) {
                break;
            }
            try {
                YierLoomPlugin provider = Objects.requireNonNull(providers.next());
                plugins.add(new DiscoveredPlugin(
                        provider,
                        PluginSource.BUILT_IN,
                        sourcePath(provider)));
                consecutiveEnumerationFailures = 0;
            } catch (Throwable failure) {
                rethrowFatal(failure);
                issues.add(new PluginIssue(Path.of("built-in"), "PROVIDER_INSTANTIATION_FAILED"));
                consecutiveEnumerationFailures++;
            }
        }
        plugins.sort((left, right) -> left.provider().getClass().getName()
                .compareTo(right.provider().getClass().getName()));
        return new Result(plugins, issues, List.of());
    }

    private static void discoverExternal(
            Path candidate,
            ClassLoader engineLoader,
            List<DiscoveredPlugin> plugins,
            List<PluginIssue> issues,
            List<YierLoomPluginClassLoader> externalLoaders
    ) {
        String providerName;
        try (JarFile jar = new JarFile(candidate.toFile())) {
            if (bundlesForbiddenClass(jar)) {
                issues.add(new PluginIssue(candidate, "BUNDLED_FORBIDDEN_CLASS"));
                return;
            }
            List<String> declarations = providerDeclarations(jar);
            if (declarations.size() != 1 || !isBinaryName(declarations.get(0))) {
                issues.add(new PluginIssue(candidate, "PROVIDER_DECLARATION_INVALID"));
                return;
            }
            providerName = declarations.get(0);
        } catch (Throwable failure) {
            rethrowFatal(failure);
            issues.add(new PluginIssue(candidate, "CANDIDATE_INVALID"));
            return;
        }

        YierLoomPluginClassLoader loader = null;
        try {
            loader = new YierLoomPluginClassLoader(candidate, engineLoader);
            Class<?> providerType = Class.forName(providerName, true, loader);
            if (providerType.getClassLoader() != loader
                    || !YierLoomPlugin.class.isAssignableFrom(providerType)) {
                issues.add(new PluginIssue(candidate, "PROVIDER_TYPE_INVALID"));
                closeQuietly(loader);
                return;
            }
            Constructor<?> constructor = providerType.getConstructor();
            YierLoomPlugin provider = (YierLoomPlugin) constructor.newInstance();
            plugins.add(new DiscoveredPlugin(provider, PluginSource.EXTERNAL, candidate));
            externalLoaders.add(loader);
        } catch (NoSuchMethodException failure) {
            closeQuietly(loader);
            issues.add(new PluginIssue(candidate, "PROVIDER_CONSTRUCTOR_INVALID"));
        } catch (Throwable failure) {
            closeQuietly(loader);
            rethrowFatal(failure);
            issues.add(new PluginIssue(candidate, "PROVIDER_INSTANTIATION_FAILED"));
        }
    }

    private static boolean bundlesForbiddenClass(JarFile jar) {
        return jar.stream()
                .map(JarEntry::getName)
                .map(PluginDiscovery::classEntryPath)
                .anyMatch(name -> name.endsWith(".class")
                        && FORBIDDEN_CLASS_ENTRIES.stream().anyMatch(name::startsWith));
    }

    private static String classEntryPath(String name) {
        String versions = "META-INF/versions/";
        if (!name.startsWith(versions)) {
            return name;
        }
        int packageStart = name.indexOf('/', versions.length());
        return packageStart < 0 ? name : name.substring(packageStart + 1);
    }

    private static List<String> providerDeclarations(JarFile jar) throws IOException {
        JarEntry service = jar.getJarEntry(SERVICE_ENTRY);
        if (service == null || service.isDirectory()) {
            return List.of();
        }
        try (InputStream input = jar.getInputStream(service)) {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return content.lines()
                    .map(line -> {
                        int comment = line.indexOf('#');
                        return (comment < 0 ? line : line.substring(0, comment)).trim();
                    })
                    .filter(line -> !line.isEmpty())
                    .toList();
        }
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

    private static Path sourcePath(YierLoomPlugin provider) {
        try {
            CodeSource codeSource = provider.getClass().getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                URI location = codeSource.getLocation().toURI();
                if ("file".equalsIgnoreCase(location.getScheme())) {
                    return Path.of(location).toAbsolutePath().normalize();
                }
            }
        } catch (Throwable failure) {
            rethrowFatal(failure);
        }
        return Path.of("built-in", provider.getClass().getName());
    }

    private static void closeQuietly(YierLoomPluginClassLoader loader) {
        if (loader == null) {
            return;
        }
        try {
            loader.close();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            // Candidate cleanup failure must not stop discovery of later JARs.
        }
    }

    private static void rethrowFatal(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof VirtualMachineError fatal) {
                throw fatal;
            }
            if (current instanceof ThreadDeath fatal) {
                throw fatal;
            }
            if (current instanceof InvocationTargetException invocation) {
                current = invocation.getTargetException();
            } else if (current instanceof ExceptionInInitializerError initializer) {
                current = initializer.getException();
            } else {
                current = current.getCause();
            }
        }
    }

    public record Result(
            List<DiscoveredPlugin> plugins,
            List<PluginIssue> issues,
            List<YierLoomPluginClassLoader> externalLoaders
    ) {
        public Result {
            plugins = List.copyOf(plugins);
            issues = List.copyOf(issues);
            externalLoaders = List.copyOf(externalLoaders);
        }
    }
}
