package com.nowcoder.yierloom.bootstrap;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;

final class BootstrapResources implements Runnable {
    private static final String API_CLASS_PREFIX = "com/nowcoder/yierloom/api/";

    private final NestedJarExtractor.Extraction extraction;
    private JarFile apiJar;
    private YierLoomEngineClassLoader engineLoader;
    private boolean engineLoaderClosed;
    private boolean apiJarClosed;
    private boolean extractionDeleted;
    private boolean closed;

    private BootstrapResources(NestedJarExtractor.Extraction extraction) {
        this.extraction = Objects.requireNonNull(extraction, "extraction");
    }

    static BootstrapResources create(NestedJarExtractor.Extraction extraction) {
        return new BootstrapResources(extraction);
    }

    void open(Instrumentation instrumentation) throws Exception {
        Objects.requireNonNull(instrumentation, "instrumentation");
        Path api = nestedLibrary(NestedJarExtractor.API_ENTRY);
        Path sdk = nestedLibrary(NestedJarExtractor.SDK_ENTRY);
        Path core = nestedLibrary(NestedJarExtractor.CORE_ENTRY);
        Path byteBuddy = nestedLibrary(NestedJarExtractor.BYTE_BUDDY_ENTRY);

        apiJar = new JarFile(api.toFile());
        instrumentation.appendToBootstrapClassLoaderSearch(apiJar);
        assertBootstrapApiIdentity(apiJar);
        engineLoader = new YierLoomEngineClassLoader(
                sdk, core, byteBuddy, ClassLoader.getSystemClassLoader());
    }

    ClassLoader engineLoader() {
        if (closed || engineLoaderClosed || engineLoader == null) {
            throw new IllegalStateException("YierLoom bootstrap resources are closed");
        }
        return engineLoader;
    }

    Path extractionDirectory() {
        return extraction.directory();
    }

    @Override
    public synchronized void run() {
        if (closed) {
            return;
        }
        Throwable failure = BootstrapFailures.capture(() -> {
            if (!engineLoaderClosed) {
                if (engineLoader != null) {
                    try {
                        engineLoader.close();
                    } catch (IOException closeFailure) {
                        throw new CleanupException(closeFailure);
                    }
                }
                engineLoaderClosed = true;
            }
        });
        failure = retain(failure, BootstrapFailures.capture(() -> {
            if (!apiJarClosed) {
                if (apiJar != null) {
                    try {
                        apiJar.close();
                    } catch (IOException closeFailure) {
                        throw new CleanupException(closeFailure);
                    }
                }
                apiJarClosed = true;
            }
        }));
        failure = retain(failure, BootstrapFailures.capture(() -> {
            if (!extractionDeleted && engineLoaderClosed && apiJarClosed) {
                NestedJarExtractor.deletePrivateDirectory(extraction, false);
                extractionDeleted = true;
            }
        }));
        closed = engineLoaderClosed && apiJarClosed && extractionDeleted;
        rethrowCleanup(failure);
    }

    private Path nestedLibrary(String entry) throws IOException {
        Path directory = extraction.directory();
        Path candidate = directory.resolve(entry).normalize();
        if (!candidate.startsWith(directory)
                || !Files.isRegularFile(candidate)
                || !Files.isReadable(candidate)) {
            throw new IOException("invalid extracted YierLoom nested library");
        }
        return candidate;
    }

    private static void assertBootstrapApiIdentity(JarFile apiJar) throws ClassNotFoundException {
        List<String> apiClasses = apiJar.stream()
                .map(entry -> entry.getName())
                .filter(name -> name.startsWith(API_CLASS_PREFIX) && name.endsWith(".class"))
                .map(name -> name.substring(0, name.length() - ".class".length())
                        .replace('/', '.'))
                .sorted()
                .toList();
        if (apiClasses.isEmpty()) {
            throw new IllegalStateException("YierLoom Plugin API JAR contains no API classes");
        }
        ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
        for (String className : apiClasses) {
            Class<?> bootstrapType = Class.forName(className, false, null);
            Class<?> systemView = Class.forName(className, false, systemLoader);
            if (bootstrapType.getClassLoader() != null || systemView != bootstrapType) {
                throw new IllegalStateException(
                        "YierLoom Plugin API has inconsistent class loader identity");
            }
        }
    }

    private static Throwable retain(Throwable current, Throwable candidate) {
        return BootstrapFailures.preferred(current, candidate);
    }

    private static void rethrowCleanup(Throwable failure) {
        if (failure == null) {
            return;
        }
        BootstrapFailures.rethrowFatal(failure);
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new CleanupException(failure);
    }

    private static final class CleanupException extends RuntimeException {
        private CleanupException(Throwable cause) {
            super(cause);
        }
    }
}
