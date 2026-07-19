package com.nowcoder.community.oss.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.oss.application.command.CreateSignedUrlCommand;
import com.nowcoder.community.oss.application.result.ObjectSignedUrlResult;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssObjectStatus;
import com.nowcoder.community.oss.domain.model.OssObjectVersionStatus;
import com.nowcoder.community.oss.domain.repository.OssAccessGrantRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.domain.service.OssObjectAccessPolicy;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStore;
import com.nowcoder.community.oss.infrastructure.storage.PresignedObjectUrl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Service
public class ObjectAccessApplicationService {

    private final OssObjectRepository objectRepository;
    private final OssObjectVersionRepository versionRepository;
    private final OssAccessGrantRepository grantRepository;
    private final ObjectStore objectStore;
    private final Clock clock;
    private final OssObjectAccessPolicy accessPolicy;

    @Autowired
    public ObjectAccessApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssAccessGrantRepository grantRepository,
            ObjectStore objectStore,
            Clock clock,
            OssObjectAccessPolicy accessPolicy
    ) {
        this.objectRepository = objectRepository;
        this.versionRepository = versionRepository;
        this.grantRepository = grantRepository;
        this.objectStore = objectStore;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.accessPolicy = accessPolicy == null ? new OssObjectAccessPolicy() : accessPolicy;
    }

    public ObjectSignedUrlResult createSignedDownloadUrl(CreateSignedUrlCommand command) {
        OssObject object = objectRepository.findById(command.objectId())
                .orElseThrow(this::objectNotFound);
        UUID versionId = command.versionId() == null ? object.currentVersionId() : command.versionId();
        if (!accessPolicy.canRead(
                object,
                versionId,
                command.actorId(),
                grantRepository.findReadGrants(object.objectId(), versionId, command.actorId()),
                clock.instant()
        )) {
            throw objectNotFound();
        }
        return createSignedDownloadUrl(command, object, versionId);
    }

    private ObjectSignedUrlResult createSignedDownloadUrl(
            CreateSignedUrlCommand command,
            OssObject object,
            UUID versionId
    ) {
        if (object.status() == OssObjectStatus.DELETE_PENDING || object.status() == OssObjectStatus.PURGED) {
            throw new IllegalStateException("object is not available for download");
        }
        if (versionId == null) {
            throw objectNotFound();
        }
        OssObjectVersion version = versionRepository.findById(versionId)
                .orElseThrow(this::objectNotFound);
        if (!object.objectId().equals(version.objectId())) {
            throw objectNotFound();
        }
        if (version.status() != OssObjectVersionStatus.ACTIVE) {
            throw new IllegalStateException("object version is not available for download");
        }
        long ttlSeconds = command.ttlSeconds() <= 0 ? 300 : Math.min(command.ttlSeconds(), 86_400);
        PresignedObjectUrl signed = objectStore.presignDownload(version.storageBucket(), version.storageKey(), Duration.ofSeconds(ttlSeconds));
        return new ObjectSignedUrlResult(signed.url(), signed.method(), signed.expiresAt(), "private, max-age=" + ttlSeconds);
    }

    public ObjectSignedUrlResult createInternalSignedDownloadUrl(
            CreateSignedUrlCommand command,
            String serviceSubject
    ) {
        OssObject object = objectRepository.findById(command.objectId())
                .orElseThrow(this::objectNotFound);
        if (serviceSubject == null || serviceSubject.isBlank()
                || !object.ownerService().equals(serviceSubject.trim())
                || "USER".equalsIgnoreCase(object.ownerType())) {
            throw objectNotFound();
        }
        UUID versionId = command.versionId() == null ? object.currentVersionId() : command.versionId();
        return createSignedDownloadUrl(command, object, versionId);
    }

    private BusinessException objectNotFound() {
        return new BusinessException(CommonErrorCode.NOT_FOUND, "OSS object not found");
    }
}
