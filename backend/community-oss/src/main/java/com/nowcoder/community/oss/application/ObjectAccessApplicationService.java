package com.nowcoder.community.oss.application;

import com.nowcoder.community.oss.application.command.CreateSignedUrlCommand;
import com.nowcoder.community.oss.application.result.ObjectSignedUrlResult;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssObjectStatus;
import com.nowcoder.community.oss.domain.model.OssObjectVersionStatus;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStore;
import com.nowcoder.community.oss.infrastructure.storage.PresignedObjectUrl;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class ObjectAccessApplicationService {

    private final OssObjectRepository objectRepository;
    private final OssObjectVersionRepository versionRepository;
    private final ObjectStore objectStore;

    public ObjectAccessApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            ObjectStore objectStore
    ) {
        this.objectRepository = objectRepository;
        this.versionRepository = versionRepository;
        this.objectStore = objectStore;
    }

    public ObjectSignedUrlResult createSignedDownloadUrl(CreateSignedUrlCommand command) {
        OssObject object = objectRepository.findById(command.objectId())
                .orElseThrow(() -> new IllegalArgumentException("object not found"));
        if (object.status() == OssObjectStatus.DELETE_PENDING || object.status() == OssObjectStatus.PURGED) {
            throw new IllegalStateException("object is not available for download");
        }
        UUID versionId = command.versionId() == null ? object.currentVersionId() : command.versionId();
        if (versionId == null) {
            throw new IllegalArgumentException("object version not found");
        }
        OssObjectVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("object version not found"));
        if (!object.objectId().equals(version.objectId())) {
            throw new IllegalArgumentException("object version does not belong to object");
        }
        if (version.status() != OssObjectVersionStatus.ACTIVE) {
            throw new IllegalStateException("object version is not available for download");
        }
        long ttlSeconds = command.ttlSeconds() <= 0 ? 300 : Math.min(command.ttlSeconds(), 86_400);
        PresignedObjectUrl signed = objectStore.presignDownload(version.storageBucket(), version.storageKey(), Duration.ofSeconds(ttlSeconds));
        return new ObjectSignedUrlResult(signed.url(), signed.method(), signed.expiresAt(), "private, max-age=" + ttlSeconds);
    }
}
