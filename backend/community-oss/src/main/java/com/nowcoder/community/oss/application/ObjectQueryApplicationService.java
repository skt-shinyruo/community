package com.nowcoder.community.oss.application;

import com.nowcoder.community.oss.application.result.ObjectDownloadResult;
import com.nowcoder.community.oss.application.result.ObjectMetadataResult;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectAlias;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.repository.OssObjectAliasRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.infrastructure.config.OssProperties;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStore;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStoreObject;
import com.nowcoder.community.oss.infrastructure.storage.StoredObject;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

@Service
public class ObjectQueryApplicationService {

    private final OssObjectRepository objectRepository;
    private final OssObjectVersionRepository versionRepository;
    private final OssObjectAliasRepository aliasRepository;
    private final ObjectStore objectStore;
    private final String publicBaseUrl;

    public ObjectQueryApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssObjectAliasRepository aliasRepository,
            ObjectStore objectStore,
            OssProperties properties
    ) {
        this.objectRepository = objectRepository;
        this.versionRepository = versionRepository;
        this.aliasRepository = aliasRepository;
        this.objectStore = objectStore;
        this.publicBaseUrl = normalizeBaseUrl(properties.publicBaseUrl());
    }

    public ObjectMetadataResult getMetadata(UUID objectId) {
        OssObject object = objectRepository.findById(objectId)
                .orElseThrow(() -> new IllegalArgumentException("object not found"));
        UUID versionId = object.currentVersionId();
        OssObjectVersion version = versionId == null ? null : versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("object version not found"));
        return toMetadataResult(object, version);
    }

    public ObjectDownloadResult resolvePublicFile(String filePath) {
        ResolvedVersion resolved = resolveVersion(filePath);
        if (resolved == null) {
            return null;
        }
        StoredObject stored = objectStore.get(resolved.version().storageBucket(), resolved.version().storageKey());
        Optional<ObjectStoreObject> head = objectStore.head(resolved.version().storageBucket(), resolved.version().storageKey());
        String etag = head.map(ObjectStoreObject::etag).orElse("");
        String cacheControl = StringUtils.hasText(resolved.version().cacheControl())
                ? resolved.version().cacheControl()
                : "public, max-age=31536000, immutable";
        return new ObjectDownloadResult(
                stored.content(),
                stored.contentType(),
                stored.contentLength(),
                etag,
                cacheControl,
                resolved.version().fileName()
        );
    }

    private ResolvedVersion resolveVersion(String filePath) {
        String normalized = filePath == null ? "" : filePath.trim();
        String[] parts = normalized.split("/", 3);
        if (parts.length >= 2) {
            try {
                UUID objectId = UUID.fromString(parts[0]);
                UUID versionId = UUID.fromString(parts[1]);
                OssObject object = objectRepository.findById(objectId).orElse(null);
                OssObjectVersion version = versionRepository.findById(versionId).orElse(null);
                return object == null || version == null ? null : new ResolvedVersion(object, version);
            } catch (IllegalArgumentException ignored) {
                // Legacy aliases are not UUID-addressed.
            }
        }
        Optional<OssObjectAlias> alias = aliasRepository.findByAliasKey(normalized);
        if (alias.isEmpty()) {
            return null;
        }
        OssObject object = objectRepository.findById(alias.get().objectId()).orElse(null);
        OssObjectVersion version = versionRepository.findById(alias.get().versionId()).orElse(null);
        if (object == null || version == null) {
            return null;
        }
        if (object.status() == com.nowcoder.community.oss.domain.model.OssObjectStatus.DELETE_PENDING
                || object.status() == com.nowcoder.community.oss.domain.model.OssObjectStatus.PURGED
                || version.status() == com.nowcoder.community.oss.domain.model.OssObjectVersionStatus.PURGED) {
            return null;
        }
        return new ResolvedVersion(object, version);
    }

    private ObjectMetadataResult toMetadataResult(OssObject object, OssObjectVersion version) {
        return new ObjectMetadataResult(
                object.objectId(),
                object.currentVersionId(),
                object.usage(),
                object.ownerService(),
                object.ownerDomain(),
                object.ownerType(),
                object.ownerId(),
                object.visibility().name(),
                object.status().name(),
                version == null ? object.latestFileName() : version.fileName(),
                version == null ? object.latestContentType() : version.contentType(),
                version == null ? object.latestContentLength() : version.contentLength(),
                version == null ? object.latestChecksumSha256() : version.checksumSha256(),
                version == null ? "" : publicBaseUrl + "/files/" + object.objectId() + "/" + version.versionId() + "/" + version.fileName()
        );
    }

    private String normalizeBaseUrl(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "http://localhost:12880";
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private record ResolvedVersion(OssObject object, OssObjectVersion version) {
    }
}
