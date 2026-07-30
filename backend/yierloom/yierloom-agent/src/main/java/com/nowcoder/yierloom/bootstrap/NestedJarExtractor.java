package com.nowcoder.yierloom.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class NestedJarExtractor {
    static final String API_ENTRY = "META-INF/yierloom/lib/yierloom-plugin-api.jar";
    static final String SDK_ENTRY = "META-INF/yierloom/lib/yierloom-bytebuddy-sdk.jar";
    static final String CORE_ENTRY = "META-INF/yierloom/lib/yierloom-agent-core.jar";
    static final String BYTE_BUDDY_ENTRY = "META-INF/yierloom/lib/byte-buddy.jar";
    static final List<String> REQUIRED_ENTRIES = List.of(
            API_ENTRY, SDK_ENTRY, CORE_ENTRY, BYTE_BUDDY_ENTRY);

    private static final OpenOption[] NEW_FILE_OPTIONS = {
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
    };
    private static final String OWNER_FILE = ".yierloom-owner";

    private NestedJarExtractor() {
    }

    static Path locateAgentJar() throws IOException {
        CodeSource source = YierLoomAgent.class.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            throw new IOException("YierLoom agent location is unavailable");
        }
        try {
            Path path = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new IOException("YierLoom agent is not a readable JAR");
            }
            return path;
        } catch (URISyntaxException | IllegalArgumentException failure) {
            throw new IOException("YierLoom agent location is invalid", failure);
        }
    }

    static Path extract(Path outerJar) throws IOException {
        Objects.requireNonNull(outerJar, "outerJar");
        return extractOwned(outerJar).directory();
    }

    static Path extract(Path outerJar, Path temporaryParent) throws IOException {
        Objects.requireNonNull(outerJar, "outerJar");
        Objects.requireNonNull(temporaryParent, "temporaryParent");
        Path parent = temporaryParent.toAbsolutePath().normalize();
        Files.createDirectories(parent);
        return extractOwned(outerJar, parent).directory();
    }

    static Extraction extractOwned(Path outerJar) throws IOException {
        Objects.requireNonNull(outerJar, "outerJar");
        return extractInto(outerJar, null);
    }

    static Extraction extractOwned(Path outerJar, Path temporaryParent) throws IOException {
        Objects.requireNonNull(outerJar, "outerJar");
        Objects.requireNonNull(temporaryParent, "temporaryParent");
        Path parent = temporaryParent.toAbsolutePath().normalize();
        Files.createDirectories(parent);
        return extractInto(outerJar, parent);
    }

    private static Extraction extractInto(Path outerJar, Path temporaryParent) throws IOException {
        Path directory = temporaryParent == null
                ? Files.createTempDirectory("yierloom-")
                : Files.createTempDirectory(temporaryParent, "yierloom-");
        directory = directory.toAbsolutePath().normalize();
        Extraction extraction = createOwnership(directory);
        try (JarFile archive = new JarFile(outerJar.toFile())) {
            for (String entryName : REQUIRED_ENTRIES) {
                extractOne(archive, directory, entryName);
            }
            return extraction;
        } catch (Throwable failure) {
            Extraction failedExtraction = extraction;
            Throwable cleanup = BootstrapFailures.capture(
                    () -> deletePrivateDirectory(failedExtraction));
            Throwable selected = BootstrapFailures.preferred(failure, cleanup);
            BootstrapFailures.rethrowFatal(selected);
            if (cleanup != null) {
                throw new IOException(
                        "unable to extract and clean YierLoom nested libraries", selected);
            }
            if (failure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw new IOException("unable to extract YierLoom nested libraries", failure);
        }
    }

    private static Extraction createOwnership(Path directory) throws IOException {
        String token = UUID.randomUUID().toString();
        Path owner = directory.resolve(OWNER_FILE).normalize();
        try {
            Files.writeString(owner, token, StandardCharsets.US_ASCII, NEW_FILE_OPTIONS);
            BasicFileAttributes directoryAttributes = Files.readAttributes(
                    directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            BasicFileAttributes ownerAttributes = Files.readAttributes(
                    owner, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!directoryAttributes.isDirectory() || !ownerAttributes.isRegularFile()) {
                throw new IOException("invalid YierLoom extraction directory identity");
            }
            return new Extraction(
                    directory,
                    directoryAttributes.fileKey(),
                    new AtomicReference<>(ownerAttributes.fileKey()),
                    token,
                    new AtomicBoolean());
        } catch (Throwable failure) {
            Throwable cleanup = BootstrapFailures.capture(() -> {
                try {
                    Files.deleteIfExists(owner);
                    Files.deleteIfExists(directory);
                } catch (IOException cleanupFailure) {
                    throw new CleanupException(cleanupFailure);
                }
            });
            Throwable selected = BootstrapFailures.preferred(failure, cleanup);
            BootstrapFailures.rethrowFatal(selected);
            if (cleanup != null) {
                throw new IOException(
                        "unable to own and clean YierLoom extraction directory", selected);
            }
            if (failure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw new IOException("unable to own YierLoom extraction directory", failure);
        }
    }

    private static void extractOne(JarFile archive, Path directory, String entryName) throws IOException {
        Path relative = Path.of(entryName);
        Path output = directory.resolve(relative).normalize();
        if (relative.isAbsolute() || !output.startsWith(directory)) {
            throw new IOException("invalid YierLoom nested library path");
        }
        JarEntry entry = archive.getJarEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("missing YierLoom nested library: " + entryName);
        }
        Path parent = output.getParent();
        if (parent == null || !parent.startsWith(directory)) {
            throw new IOException("invalid YierLoom nested library path");
        }
        Files.createDirectories(parent);
        try (InputStream input = archive.getInputStream(entry);
             OutputStream target = Files.newOutputStream(output, NEW_FILE_OPTIONS)) {
            input.transferTo(target);
        }
    }

    static void deletePrivateDirectory(Extraction extraction) {
        deletePrivateDirectory(extraction, true);
    }

    static void deletePrivateDirectory(Extraction extraction, boolean registerRetry) {
        if (extraction == null) {
            return;
        }
        Path root = extraction.directory();
        if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Throwable failure = BootstrapFailures.capture(() -> deleteOwnedTree(extraction));
        if (failure != null && registerRetry) {
            failure = retain(failure, registerCleanupRetry(extraction));
        }
        throwCleanupFailure(failure);
    }

    private static void deleteOwnedTree(Extraction extraction) {
        Path root = extraction.directory();
        Path parent = root.getParent();
        Path rootName = root.getFileName();
        if (parent == null || rootName == null) {
            throw new CleanupException();
        }
        try {
            try (DirectoryStream<Path> parentStream = Files.newDirectoryStream(parent)) {
                if (!(parentStream instanceof SecureDirectoryStream<Path> secureParent)) {
                    throw new CleanupException();
                }
                try (SecureDirectoryStream<Path> secureRoot = secureParent.newDirectoryStream(
                        rootName, LinkOption.NOFOLLOW_LINKS)) {
                    verifyOwnership(extraction, secureRoot);
                    Throwable failure = deleteChildren(secureRoot);
                    if (failure != null) {
                        throwCleanupFailure(failure);
                    }
                    secureRoot.deleteFile(Path.of(OWNER_FILE));
                    try {
                        secureParent.deleteDirectory(rootName);
                    } catch (IOException deleteFailure) {
                        Throwable restoreFailure = BootstrapFailures.capture(
                                () -> restoreOwner(extraction, secureRoot));
                        throwCleanupFailure(BootstrapFailures.preferred(
                                new CleanupException(deleteFailure), restoreFailure));
                    }
                }
            }
        } catch (IOException failure) {
            throw new CleanupException(failure);
        }
    }

    private static void verifyOwnership(
            Extraction extraction,
            SecureDirectoryStream<Path> directory
    ) {
        try {
            BasicFileAttributes rootAttributes = directory
                    .getFileAttributeView(BasicFileAttributeView.class)
                    .readAttributes();
            if (!rootAttributes.isDirectory()
                    || extraction.directoryKey() != null
                    && !extraction.directoryKey().equals(rootAttributes.fileKey())) {
                throw new CleanupException();
            }
            BasicFileAttributes ownerAttributes = directory
                    .getFileAttributeView(
                            Path.of(OWNER_FILE),
                            BasicFileAttributeView.class,
                            LinkOption.NOFOLLOW_LINKS)
                    .readAttributes();
            Object expectedOwnerKey = extraction.ownerKey().get();
            if (!ownerAttributes.isRegularFile()
                    || expectedOwnerKey != null
                    && !expectedOwnerKey.equals(ownerAttributes.fileKey())
                    || !extraction.token().equals(readOwnerToken(directory))) {
                throw new CleanupException();
            }
        } catch (IOException failure) {
            throw new CleanupException(failure);
        }
    }

    private static String readOwnerToken(SecureDirectoryStream<Path> directory) throws IOException {
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (SeekableByteChannel channel = directory.newByteChannel(Path.of(OWNER_FILE), options)) {
            ByteBuffer buffer = ByteBuffer.allocate(128);
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                // The ownership token is deliberately bounded.
            }
            if (channel.read(ByteBuffer.allocate(1)) != -1) {
                throw new IOException("invalid YierLoom extraction directory identity");
            }
            buffer.flip();
            return StandardCharsets.US_ASCII.decode(buffer).toString();
        }
    }

    private static void restoreOwner(
            Extraction extraction,
            SecureDirectoryStream<Path> directory
    ) {
        Path owner = Path.of(OWNER_FILE);
        try {
            try {
                directory.getFileAttributeView(
                                owner,
                                BasicFileAttributeView.class,
                                LinkOption.NOFOLLOW_LINKS)
                        .readAttributes();
                return;
            } catch (NoSuchFileException missingOwner) {
                // Recreate the marker through the still-open owned directory handle.
            }
            Set<OpenOption> options = Set.of(
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS);
            try (SeekableByteChannel channel = directory.newByteChannel(owner, options)) {
                ByteBuffer token = StandardCharsets.US_ASCII.encode(extraction.token());
                while (token.hasRemaining()) {
                    channel.write(token);
                }
            }
            BasicFileAttributes attributes = directory.getFileAttributeView(
                            owner,
                            BasicFileAttributeView.class,
                            LinkOption.NOFOLLOW_LINKS)
                    .readAttributes();
            if (!attributes.isRegularFile()) {
                throw new CleanupException();
            }
            extraction.ownerKey().set(attributes.fileKey());
        } catch (IOException failure) {
            throw new CleanupException(failure);
        }
    }

    private static Throwable deleteChildren(SecureDirectoryStream<Path> directory) {
        Throwable failure = null;
        try {
            for (Path entry : directory) {
                Path name = entry.getFileName();
                if (name == null || OWNER_FILE.equals(name.toString())) {
                    continue;
                }
                BasicFileAttributes attributes;
                try {
                    attributes = directory.getFileAttributeView(
                                    name,
                                    BasicFileAttributeView.class,
                                    LinkOption.NOFOLLOW_LINKS)
                            .readAttributes();
                } catch (Throwable attributeFailure) {
                    failure = retain(failure, attributeFailure);
                    continue;
                }
                if (attributes.isDirectory() && !attributes.isSymbolicLink()) {
                    try (SecureDirectoryStream<Path> child = directory.newDirectoryStream(
                            name, LinkOption.NOFOLLOW_LINKS)) {
                        failure = retain(failure, deleteChildren(child));
                    } catch (Throwable childFailure) {
                        failure = retain(failure, childFailure);
                    }
                    failure = retain(failure, BootstrapFailures.capture(
                            () -> deleteDirectory(directory, name)));
                } else {
                    failure = retain(failure, BootstrapFailures.capture(
                            () -> deleteFile(directory, name)));
                }
            }
        } catch (Throwable iterationFailure) {
            failure = retain(failure, iterationFailure);
        }
        return failure;
    }

    private static void deleteDirectory(SecureDirectoryStream<Path> directory, Path name) {
        try {
            directory.deleteDirectory(name);
        } catch (IOException failure) {
            throw new CleanupException(failure);
        }
    }

    private static void deleteFile(SecureDirectoryStream<Path> directory, Path name) {
        try {
            directory.deleteFile(name);
        } catch (IOException failure) {
            throw new CleanupException(failure);
        }
    }

    private static Throwable registerCleanupRetry(Extraction extraction) {
        if (!extraction.cleanupHookRegistered().compareAndSet(false, true)) {
            return null;
        }
        try {
            Thread cleanup = new Thread(
                    () -> runCleanupRetry(extraction),
                    "yierloom-bootstrap-cleanup");
            cleanup.setDaemon(true);
            Runtime.getRuntime().addShutdownHook(cleanup);
            return null;
        } catch (Throwable failure) {
            extraction.cleanupHookRegistered().set(false);
            return failure;
        }
    }

    private static void runCleanupRetry(Extraction extraction) {
        try {
            deletePrivateDirectory(extraction, false);
        } catch (Throwable failure) {
            BootstrapFailures.rethrowFatal(failure);
            // The retry is best-effort and must not expose private paths at JVM exit.
        }
    }

    private static Throwable retain(Throwable current, Throwable candidate) {
        return BootstrapFailures.preferred(current, candidate);
    }

    private static void throwCleanupFailure(Throwable failure) {
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

    record Extraction(
            Path directory,
            Object directoryKey,
            AtomicReference<Object> ownerKey,
            String token,
            AtomicBoolean cleanupHookRegistered
    ) {
        Extraction {
            directory = Objects.requireNonNull(directory, "directory")
                    .toAbsolutePath()
                    .normalize();
            ownerKey = Objects.requireNonNull(ownerKey, "ownerKey");
            token = Objects.requireNonNull(token, "token");
            cleanupHookRegistered = Objects.requireNonNull(
                    cleanupHookRegistered, "cleanupHookRegistered");
        }

        Path ownerFile() {
            return directory.resolve(OWNER_FILE).normalize();
        }
    }

    private static final class CleanupException extends RuntimeException {
        private CleanupException() {
            super("YierLoom private directory cleanup failed");
        }

        private CleanupException(Throwable cause) {
            super("YierLoom private directory cleanup failed", cause);
        }
    }
}
