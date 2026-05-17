package com.nowcoder.community.oss.application;

import com.nowcoder.community.common.spring.feature.FeatureFlagDecisions;
import com.nowcoder.community.common.spring.feature.FeatureFlagProperties;
import com.nowcoder.community.common.spring.policy.UploadPolicyDecisions;
import com.nowcoder.community.common.spring.policy.UploadPolicyProperties;
import com.nowcoder.community.oss.application.command.CompleteObjectUploadCommand;
import com.nowcoder.community.oss.application.command.ObjectUploadContent;
import com.nowcoder.community.oss.application.command.PrepareObjectUploadCommand;
import com.nowcoder.community.oss.application.result.ObjectMetadataResult;
import com.nowcoder.community.oss.application.result.ObjectUploadSessionResult;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssUploadSession;
import com.nowcoder.community.oss.domain.model.OssUsagePolicy;
import com.nowcoder.community.oss.domain.model.OssVisibility;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.domain.repository.OssUploadSessionRepository;
import com.nowcoder.community.oss.domain.repository.OssUsagePolicyRepository;
import com.nowcoder.community.oss.infrastructure.config.OssProperties;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStore;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStoreObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class ObjectUploadApplicationService {

    private static final String UPLOAD_MODE_PROXY = "PROXY";
    private static final String USAGE_USER_AVATAR = "USER_AVATAR";
    private static final String FEATURE_FILE_UPLOAD = "file-upload";
    private static final Duration DEFAULT_SESSION_TTL = Duration.ofMinutes(15);

    private final OssObjectRepository objectRepository;
    private final OssObjectVersionRepository versionRepository;
    private final OssUploadSessionRepository uploadSessionRepository;
    private final OssUsagePolicyRepository policyRepository;
    private final ObjectStore objectStore;
    private final String storageBucket;
    private final String publicBaseUrl;
    private final Clock clock;
    private final UploadPolicyDecisions uploadPolicyDecisions;
    private final FeatureFlagDecisions featureFlags;

    @Autowired
    public ObjectUploadApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssUploadSessionRepository uploadSessionRepository,
            OssUsagePolicyRepository policyRepository,
            ObjectStore objectStore,
            OssProperties properties,
            Clock clock,
            UploadPolicyDecisions uploadPolicyDecisions,
            FeatureFlagDecisions featureFlags
    ) {
        this(
                objectRepository,
                versionRepository,
                uploadSessionRepository,
                policyRepository,
                objectStore,
                properties.objectStore().bucket(),
                properties.publicBaseUrl(),
                clock,
                uploadPolicyDecisions,
                featureFlags
        );
    }

    public ObjectUploadApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssUploadSessionRepository uploadSessionRepository,
            ObjectStore objectStore,
            String storageBucket,
            String publicBaseUrl,
            Clock clock
    ) {
        this(
                objectRepository,
                versionRepository,
                uploadSessionRepository,
                null,
                objectStore,
                storageBucket,
                publicBaseUrl,
                clock,
                defaultUploadPolicyDecisions(),
                defaultFeatureFlagDecisions()
        );
    }

    public ObjectUploadApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssUploadSessionRepository uploadSessionRepository,
            OssUsagePolicyRepository policyRepository,
            ObjectStore objectStore,
            String storageBucket,
            String publicBaseUrl,
            Clock clock
    ) {
        this(
                objectRepository,
                versionRepository,
                uploadSessionRepository,
                policyRepository,
                objectStore,
                storageBucket,
                publicBaseUrl,
                clock,
                defaultUploadPolicyDecisions(),
                defaultFeatureFlagDecisions()
        );
    }

    public ObjectUploadApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssUploadSessionRepository uploadSessionRepository,
            OssUsagePolicyRepository policyRepository,
            ObjectStore objectStore,
            String storageBucket,
            String publicBaseUrl,
            Clock clock,
            UploadPolicyDecisions uploadPolicyDecisions
    ) {
        this(
                objectRepository,
                versionRepository,
                uploadSessionRepository,
                policyRepository,
                objectStore,
                storageBucket,
                publicBaseUrl,
                clock,
                uploadPolicyDecisions,
                defaultFeatureFlagDecisions()
        );
    }

    public ObjectUploadApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssUploadSessionRepository uploadSessionRepository,
            OssUsagePolicyRepository policyRepository,
            ObjectStore objectStore,
            String storageBucket,
            String publicBaseUrl,
            Clock clock,
            UploadPolicyDecisions uploadPolicyDecisions,
            FeatureFlagDecisions featureFlags
    ) {
        this.objectRepository = objectRepository;
        this.versionRepository = versionRepository;
        this.uploadSessionRepository = uploadSessionRepository;
        this.policyRepository = policyRepository;
        this.objectStore = objectStore;
        this.storageBucket = requireText(storageBucket, "storageBucket");
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.uploadPolicyDecisions = uploadPolicyDecisions == null ? defaultUploadPolicyDecisions() : uploadPolicyDecisions;
        this.featureFlags = featureFlags == null ? defaultFeatureFlagDecisions() : featureFlags;
    }

    @Transactional
    public ObjectUploadSessionResult prepareUpload(PrepareObjectUploadCommand command) {
        requirePrepareCommand(command);
        validateUploadPolicyChannel(command.usage());
        String safeFileName = safeFileName(command.fileName());
        validateGlobalUploadPolicy(safeFileName, command.contentType(), command.contentLength());
        Optional<OssUsagePolicy> policy = usagePolicy(command.usage());
        policy.ifPresent(value -> value.validateUpload(
                command.contentType(),
                command.contentLength(),
                command.checksumSha256()
        ));
        Instant now = clock.instant();
        UUID objectId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String storageKey = storageKey(objectId, versionId, safeFileName);

        OssObject object = OssObject.stage(
                objectId,
                command.usage(),
                command.ownerService(),
                command.ownerDomain(),
                command.ownerType(),
                command.ownerId(),
                resolveVisibility(command.visibility(), policy),
                command.actorId(),
                now
        );
        OssObjectVersion version = OssObjectVersion.staged(
                versionId,
                objectId,
                "S3_COMPATIBLE",
                storageBucket,
                storageKey,
                safeFileName,
                command.contentType(),
                command.contentLength(),
                command.checksumSha256(),
                now
        );
        OssUploadSession session = OssUploadSession.ready(
                sessionId,
                objectId,
                versionId,
                UPLOAD_MODE_PROXY,
                command.ownerService(),
                command.ownerDomain(),
                command.ownerType(),
                command.ownerId(),
                safeFileName,
                command.contentType(),
                command.contentLength(),
                command.checksumSha256(),
                command.actorId(),
                now,
                now.plus(uploadTtl(policy))
        );

        objectRepository.save(object);
        versionRepository.save(version);
        uploadSessionRepository.save(session);

        return new ObjectUploadSessionResult(
                sessionId,
                objectId,
                versionId,
                UPLOAD_MODE_PROXY,
                "/api/oss/objects/" + objectId + "/complete",
                session.expiresAt()
        );
    }

    @Transactional
    public ObjectMetadataResult completeUpload(CompleteObjectUploadCommand command) {
        OssUploadSession session = uploadSessionRepository.findById(command.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("upload session not found"));
        if (!session.objectId().equals(command.objectId()) || !session.versionId().equals(command.versionId())) {
            throw new IllegalArgumentException("upload command does not match session");
        }

        Instant now = clock.instant();
        if (session.expiredAt(now)) {
            throw new IllegalStateException("upload session expired");
        }

        OssObject object = objectRepository.findById(command.objectId())
                .orElseThrow(() -> new IllegalArgumentException("object not found"));
        OssObjectVersion version = versionRepository.findById(command.versionId())
                .orElseThrow(() -> new IllegalArgumentException("object version not found"));
        validateUploadPolicyChannel(object.usage());
        ObjectUploadContent content = command.content();
        validateContent(session, content);
        validateGlobalUploadPolicy(version.fileName(), content.contentType(), content.contentLength());
        if (!object.objectId().equals(version.objectId())) {
            throw new IllegalArgumentException("object version does not belong to object");
        }
        usagePolicy(object.usage()).ifPresent(policy -> policy.validateUpload(
                content.contentType(),
                content.contentLength(),
                content.checksumSha256()
        ));

        try (InputStream in = content.openStream()) {
            objectStore.put(version.storageBucket(), version.storageKey(), in, content.contentLength(), content.contentType());
        } catch (Exception e) {
            throw new IllegalStateException("failed to store object content", e);
        }

        Optional<ObjectStoreObject> stored = objectStore.head(version.storageBucket(), version.storageKey());
        String etag = stored.map(ObjectStoreObject::etag).orElse("");
        OssObjectVersion uploadedVersion = version.withUploadedContent(
                content.contentType(),
                content.contentLength(),
                content.checksumSha256()
        );
        OssObjectVersion activatedVersion = uploadedVersion.activate(etag, now);
        OssObject activatedObject = object.activate(activatedVersion, now);
        OssUploadSession completedSession = session.complete(now);

        versionRepository.save(activatedVersion);
        objectRepository.save(activatedObject);
        uploadSessionRepository.save(completedSession);

        return toMetadataResult(activatedObject, activatedVersion);
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
                version.fileName(),
                version.contentType(),
                version.contentLength(),
                version.checksumSha256(),
                publicBaseUrl + "/files/" + object.objectId() + "/" + version.versionId() + "/" + version.fileName()
        );
    }

    private void validateContent(OssUploadSession session, ObjectUploadContent content) {
        if (!"application/octet-stream".equals(session.expectedContentType())
                && !session.expectedContentType().equals(content.contentType())) {
            throw new IllegalArgumentException("upload content type does not match session");
        }
        if (session.expectedContentLength() > 0 && session.expectedContentLength() != content.contentLength()) {
            throw new IllegalArgumentException("upload content length does not match session");
        }
        if (!session.expectedChecksumSha256().isBlank()
                && !session.expectedChecksumSha256().equals(content.checksumSha256())) {
            throw new IllegalArgumentException("upload checksum does not match session");
        }
    }

    private void validateGlobalUploadPolicy(String fileName, String contentType, long contentLength) {
        if (!featureFlags.enabledOrDefault(FEATURE_FILE_UPLOAD, true)) {
            throw new IllegalStateException("file upload is disabled by feature flag");
        }
        if (!uploadPolicyDecisions.allowsFileSize(contentLength)) {
            throw new IllegalArgumentException("upload exceeds global max file size");
        }
        if (!uploadPolicyDecisions.allowsMimeType(contentType)) {
            throw new IllegalArgumentException("content type is not allowed by global upload policy");
        }
        if (!uploadPolicyDecisions.allowsFileName(fileName)) {
            throw new IllegalArgumentException("file extension is not allowed by global upload policy");
        }
    }

    private void validateUploadPolicyChannel(String usage) {
        if (USAGE_USER_AVATAR.equals(usage) && !uploadPolicyDecisions.avatarUploadEnabled()) {
            throw new IllegalStateException("avatar upload is disabled by upload policy");
        }
        if (!USAGE_USER_AVATAR.equals(usage) && !uploadPolicyDecisions.mediaUploadEnabled()) {
            throw new IllegalStateException("media upload is disabled by upload policy");
        }
    }

    private static UploadPolicyDecisions defaultUploadPolicyDecisions() {
        return new UploadPolicyDecisions(new UploadPolicyProperties());
    }

    private static FeatureFlagDecisions defaultFeatureFlagDecisions() {
        return new FeatureFlagDecisions(new FeatureFlagProperties());
    }

    private void requirePrepareCommand(PrepareObjectUploadCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        requireText(command.usage(), "usage");
        requireText(command.ownerService(), "ownerService");
        requireText(command.ownerDomain(), "ownerDomain");
        requireText(command.ownerType(), "ownerType");
        requireText(command.ownerId(), "ownerId");
        requireText(command.fileName(), "fileName");
    }

    private Optional<OssUsagePolicy> usagePolicy(String usage) {
        if (policyRepository == null) {
            return Optional.empty();
        }
        Optional<OssUsagePolicy> policy = policyRepository.findByUsage(usage);
        return policy == null ? Optional.empty() : policy;
    }

    private Duration uploadTtl(Optional<OssUsagePolicy> policy) {
        return policy
                .map(value -> Duration.ofSeconds(value.uploadTtlSeconds()))
                .orElse(DEFAULT_SESSION_TTL);
    }

    private OssVisibility resolveVisibility(String requestedVisibility, Optional<OssUsagePolicy> policy) {
        if (requestedVisibility != null && !requestedVisibility.isBlank()) {
            return parseVisibility(requestedVisibility);
        }
        return policy
                .map(OssUsagePolicy::defaultVisibility)
                .orElseGet(() -> parseVisibility(requestedVisibility));
    }

    private OssVisibility parseVisibility(String value) {
        String normalized = requireText(value, "visibility").trim().toUpperCase();
        try {
            return OssVisibility.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unsupported visibility: " + value, e);
        }
    }

    private String storageKey(UUID objectId, UUID versionId, String fileName) {
        return "objects/" + objectId + "/" + versionId + "/" + fileName;
    }

    private String safeFileName(String fileName) {
        String normalized = requireText(fileName, "fileName").replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        normalized = normalized.replaceAll("[^A-Za-z0-9._-]", "_");
        while (normalized.contains("..")) {
            normalized = normalized.replace("..", ".");
        }
        if (normalized.isBlank() || ".".equals(normalized)) {
            return "object.bin";
        }
        return normalized;
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null || value.isBlank() ? "http://localhost:18090" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
