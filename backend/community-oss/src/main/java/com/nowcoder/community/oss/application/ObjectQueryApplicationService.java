package com.nowcoder.community.oss.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.oss.application.result.ObjectDownloadResult;
import com.nowcoder.community.oss.application.result.ObjectMetadataResult;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectStatus;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssObjectVersionStatus;
import com.nowcoder.community.oss.domain.model.OssVisibility;
import com.nowcoder.community.oss.domain.repository.OssAccessGrantRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.domain.service.OssObjectAccessPolicy;
import com.nowcoder.community.oss.infrastructure.config.OssProperties;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStore;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStoreObject;
import com.nowcoder.community.oss.infrastructure.storage.StoredObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class ObjectQueryApplicationService {

    private final OssObjectRepository objectRepository;
    private final OssObjectVersionRepository versionRepository;
    private final OssAccessGrantRepository grantRepository;
    private final ObjectStore objectStore;
    private final String publicBaseUrl;
    private final Clock clock;
    private final OssObjectAccessPolicy accessPolicy;

    @Autowired
    public ObjectQueryApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssAccessGrantRepository grantRepository,
            ObjectStore objectStore,
            OssProperties properties,
            Clock clock,
            OssObjectAccessPolicy accessPolicy
    ) {
        this.objectRepository = objectRepository;
        this.versionRepository = versionRepository;
        this.grantRepository = grantRepository;
        this.objectStore = objectStore;
        this.publicBaseUrl = normalizeBaseUrl(properties.publicBaseUrl());
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.accessPolicy = accessPolicy;
    }

    public ObjectQueryApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssAccessGrantRepository grantRepository,
            ObjectStore objectStore,
            OssProperties properties,
            Clock clock
    ) {
        this(objectRepository, versionRepository, grantRepository, objectStore, properties, clock,
                new OssObjectAccessPolicy());
    }

    public ObjectMetadataResult getMetadata(UUID objectId, String actorId) {
        OssObject object = objectRepository.findById(objectId).orElseThrow(this::objectNotFound);
        UUID versionId = object.currentVersionId();
        if (!accessPolicy.canRead(
                object,
                versionId,
                actorId,
                grantRepository.findReadGrants(object.objectId(), versionId, actorId),
                clock.instant()
        )) {
            throw objectNotFound();
        }
        OssObjectVersion version = versionId == null ? null : versionRepository.findById(versionId)
                .orElseThrow(this::objectNotFound);
        if (version != null && !object.objectId().equals(version.objectId())) {
            throw objectNotFound();
        }
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
                return availablePublicVersion(object, version) ? new ResolvedVersion(object, version) : null;
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean availablePublicVersion(OssObject object, OssObjectVersion version) {
        return object != null
                && version != null
                && object.objectId().equals(version.objectId())
                && object.visibility() == OssVisibility.PUBLIC
                && object.status() == OssObjectStatus.ACTIVE
                && version.status() == OssObjectVersionStatus.ACTIVE;
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

    private BusinessException objectNotFound() {
        return new BusinessException(CommonErrorCode.NOT_FOUND, "OSS object not found");
    }

    private record ResolvedVersion(OssObject object, OssObjectVersion version) {
    }
}
