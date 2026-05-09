package com.nowcoder.community.oss.infrastructure.storage;

import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public class LocalFilesystemObjectStore implements ObjectStore {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final Path rootDirectory;
    private final String publicBaseUrl;

    public LocalFilesystemObjectStore(Path rootDirectory, String publicBaseUrl) {
        if (rootDirectory == null) {
            throw new IllegalArgumentException("rootDirectory must not be null");
        }
        this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
    }

    @Override
    public void put(String bucket, String key, InputStream content, long contentLength, String contentType) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        Path target = resolveDataPath(bucket, key);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            long copied = Files.copy(content, target);
            if (contentLength >= 0 && copied != contentLength) {
                Files.deleteIfExists(target);
                throw new IllegalArgumentException("contentLength mismatch: expected " + contentLength + ", copied " + copied);
            }
            writeMetadata(target, contentType);
        } catch (IOException e) {
            throw new ObjectStoreException("failed to put object " + key, e);
        }
    }

    @Override
    public Optional<ObjectStoreObject> head(String bucket, String key) {
        Path target = resolveDataPath(bucket, key);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            long length = Files.size(target);
            Instant lastModified = Files.getLastModifiedTime(target).toInstant();
            String etag = Long.toHexString(length) + "-" + Long.toHexString(lastModified.toEpochMilli());
            return Optional.of(new ObjectStoreObject(bucket, key, readContentType(target), length, etag, lastModified));
        } catch (IOException e) {
            throw new ObjectStoreException("failed to head object " + key, e);
        }
    }

    @Override
    public StoredObject get(String bucket, String key) {
        Path target = resolveDataPath(bucket, key);
        if (!Files.isRegularFile(target)) {
            throw new IllegalArgumentException("object not found: " + key);
        }
        try {
            return new StoredObject(Files.newInputStream(target), readContentType(target), Files.size(target));
        } catch (IOException e) {
            throw new ObjectStoreException("failed to get object " + key, e);
        }
    }

    @Override
    public void delete(String bucket, String key) {
        Path target = resolveDataPath(bucket, key);
        try {
            Files.deleteIfExists(target);
            Files.deleteIfExists(metadataPath(target));
        } catch (IOException e) {
            throw new ObjectStoreException("failed to delete object " + key, e);
        }
    }

    @Override
    public PresignedObjectUrl presignUpload(String bucket, String key, Duration ttl, String contentType) {
        return new PresignedObjectUrl(
                publicBaseUrl + "/internal/oss/local-upload/" + urlEncode(bucket) + "/" + urlEncode(key),
                "PUT",
                Instant.now().plus(normalizeTtl(ttl)),
                Map.of("Content-Type", normalizeContentType(contentType))
        );
    }

    @Override
    public PresignedObjectUrl presignDownload(String bucket, String key, Duration ttl) {
        return new PresignedObjectUrl(
                publicBaseUrl + "/files/" + canonicalPublicPath(key),
                "GET",
                Instant.now().plus(normalizeTtl(ttl)),
                Map.of()
        );
    }

    private Path resolveDataPath(String bucket, String key) {
        String normalizedBucket = normalizePathSegment(bucket, "bucket");
        String normalizedKey = normalizeObjectKey(key);
        Path bucketRoot = rootDirectory.resolve(normalizedBucket).normalize();
        Path target = bucketRoot.resolve(normalizedKey).normalize();
        if (!target.startsWith(bucketRoot)) {
            throw new IllegalArgumentException("object key escapes bucket root");
        }
        return target;
    }

    private void writeMetadata(Path target, String contentType) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("contentType", normalizeContentType(contentType));
        try (OutputStream out = Files.newOutputStream(metadataPath(target))) {
            properties.store(out, null);
        }
    }

    private String readContentType(Path target) {
        Path metadata = metadataPath(target);
        if (!Files.isRegularFile(metadata)) {
            return DEFAULT_CONTENT_TYPE;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(metadata)) {
            properties.load(in);
            return normalizeContentType(properties.getProperty("contentType"));
        } catch (IOException e) {
            return DEFAULT_CONTENT_TYPE;
        }
    }

    private Path metadataPath(Path target) {
        return target.resolveSibling(target.getFileName() + ".metadata");
    }

    private String normalizePathSegment(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.contains("/") || normalized.contains("\\") || normalized.contains("..") || normalized.contains("\u0000")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return normalized;
    }

    private String normalizeObjectKey(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("key must not be blank");
        }
        String normalized = value.trim();
        if (normalized.startsWith("/") || normalized.contains("\\") || normalized.contains("..") || normalized.contains("\u0000")) {
            throw new IllegalArgumentException("key is invalid");
        }
        return normalized;
    }

    private String canonicalPublicPath(String key) {
        String normalized = normalizeObjectKey(key);
        String prefix = "objects/";
        if (normalized.startsWith(prefix)) {
            return normalized.substring(prefix.length());
        }
        return normalized;
    }

    private String normalizeBaseUrl(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "http://localhost:18090";
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeContentType(String value) {
        return StringUtils.hasText(value) ? value.trim() : DEFAULT_CONTENT_TYPE;
    }

    private Duration normalizeTtl(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return Duration.ofMinutes(5);
        }
        return ttl;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
