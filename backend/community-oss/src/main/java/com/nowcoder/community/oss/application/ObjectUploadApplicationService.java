package com.nowcoder.community.oss.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
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
import com.nowcoder.community.oss.domain.model.OssUploadSessionStatus;
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Objects;
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
    private final ObjectUploadTransactionOperations transactionOperations;

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
            FeatureFlagDecisions featureFlags,
            ObjectUploadTransactionOperations transactionOperations
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
                featureFlags,
                transactionOperations
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
                defaultFeatureFlagDecisions(),
                new ObjectUploadTransactionOperations(
                        objectRepository, versionRepository, uploadSessionRepository)
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
                defaultFeatureFlagDecisions(),
                new ObjectUploadTransactionOperations(
                        objectRepository, versionRepository, uploadSessionRepository)
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
                defaultFeatureFlagDecisions(),
                new ObjectUploadTransactionOperations(
                        objectRepository, versionRepository, uploadSessionRepository)
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
                featureFlags,
                new ObjectUploadTransactionOperations(
                        objectRepository, versionRepository, uploadSessionRepository)
        );
    }

    private ObjectUploadApplicationService(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssUploadSessionRepository uploadSessionRepository,
            OssUsagePolicyRepository policyRepository,
            ObjectStore objectStore,
            String storageBucket,
            String publicBaseUrl,
            Clock clock,
            UploadPolicyDecisions uploadPolicyDecisions,
            FeatureFlagDecisions featureFlags,
            ObjectUploadTransactionOperations transactionOperations
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
        this.transactionOperations = transactionOperations;
    }

    public ObjectUploadSessionResult prepareUpload(PrepareObjectUploadCommand command) {
        requirePrepareCommand(command);
        return prepareUploadCore(command, null);
    }

    private ObjectUploadSessionResult prepareUploadCore(
            PrepareObjectUploadCommand command,
            String internalServiceSubject
    ) {
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
        UUID requestId = command.requestId() == null ? UUID.randomUUID() : command.requestId();
        OssVisibility visibility = resolveVisibility(command.visibility(), policy);
        UUID sessionId = requestId;
        UUID objectId = deterministicId(requestId, "object");
        UUID versionId = deterministicId(requestId, "version");
        String storageKey = storageKey(objectId, versionId, safeFileName);

        OssObject object = OssObject.stage(
                objectId,
                command.usage(),
                command.ownerService(),
                command.ownerDomain(),
                command.ownerType(),
                command.ownerId(),
                visibility,
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
                requestId,
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

        if (!transactionOperations.createPreparedUpload(object, version, session)) {
            OssUploadSession concurrentWinner = uploadSessionRepository.findByRequestId(requestId)
                    .orElseThrow(() -> new IllegalStateException(
                            "upload prepare request was claimed but cannot be reloaded"));
            if (internalServiceSubject != null) {
                requireInternalPrepareReplayOwner(concurrentWinner, internalServiceSubject);
            }
            validatePrepareReplay(command, safeFileName, visibility, concurrentWinner);
            if (concurrentWinner.status() == OssUploadSessionStatus.READY
                    && concurrentWinner.expiredAt(now)) {
                transactionOperations.renewReadySession(
                        concurrentWinner.sessionId(),
                        concurrentWinner.expiresAt(),
                        now.plus(uploadTtl(policy)),
                        now
                );
                concurrentWinner = uploadSessionRepository.findByRequestId(requestId)
                        .orElseThrow(() -> new IllegalStateException(
                                "renewed upload prepare request cannot be reloaded"));
            }
            return toUploadSessionResult(concurrentWinner);
        }

        return toUploadSessionResult(session);
    }

    public ObjectUploadSessionResult prepareInternalUpload(
            String serviceSubject,
            PrepareObjectUploadCommand command
    ) {
        requirePrepareCommand(command);
        if (serviceSubject == null || serviceSubject.isBlank()
                || !requireText(command.ownerService(), "ownerService").equals(serviceSubject.trim())
                || "USER".equalsIgnoreCase(command.ownerType())) {
            throw objectNotFound();
        }
        ObjectUploadSessionResult prepared = prepareUploadCore(command, serviceSubject.trim());
        return new ObjectUploadSessionResult(
                prepared.sessionId(),
                prepared.objectId(),
                prepared.versionId(),
                prepared.uploadMode(),
                "/internal/oss/upload-sessions/" + prepared.sessionId() + "/complete",
                prepared.expiresAt()
        );
    }

    public ObjectMetadataResult completeUpload(CompleteObjectUploadCommand command) {
        requireCompleteCommand(command);
        OssUploadSession session = uploadSessionRepository.findById(command.sessionId())
                .orElseThrow(this::objectNotFound);
        return completeUpload(command, session);
    }

    private ObjectMetadataResult completeUpload(
            CompleteObjectUploadCommand command,
            OssUploadSession session
    ) {
        if (!Objects.equals(command.actorId(), session.createdBy())) {
            throw objectNotFound();
        }
        if (!session.objectId().equals(command.objectId()) || !session.versionId().equals(command.versionId())) {
            throw objectNotFound();
        }

        if (session.status() == OssUploadSessionStatus.COMPLETED) {
            return canonicalMetadata(command.objectId(), command.versionId());
        }
        if (session.status() != OssUploadSessionStatus.READY) {
            throw new IllegalStateException("upload session completion is already in progress");
        }

        Instant now = clock.instant();
        if (session.expiredAt(now)) {
            throw new IllegalStateException("upload session expired");
        }

        UploadTarget target = loadUploadTarget(command.objectId(), command.versionId());
        OssObject object = target.object();
        OssObjectVersion version = target.version();
        validateUploadPolicyChannel(object.usage());
        ObjectUploadContent content = command.content();
        validateContent(session, content);
        validateGlobalUploadPolicy(version.fileName(), content.contentType(), content.contentLength());
        usagePolicy(object.usage()).ifPresent(policy -> policy.validateUpload(
                content.contentType(),
                content.contentLength(),
                content.checksumSha256()
        ));

        Optional<OssUploadSession> claimed = transactionOperations.claimCompletion(session.sessionId(), now);
        if (claimed.isEmpty()) {
            OssUploadSession current = uploadSessionRepository.findById(session.sessionId())
                    .orElseThrow(() -> new IllegalStateException("upload session disappeared during claim"));
            if (current.status() == OssUploadSessionStatus.COMPLETED) {
                return canonicalMetadata(command.objectId(), command.versionId());
            }
            throw new IllegalStateException("upload session completion is already in progress");
        }
        OssUploadSession uploadingSession = claimed.get();
        String attemptStorageKey = attemptStorageKey(uploadingSession);

        try (InputStream in = content.openStream()) {
            objectStore.put(
                    version.storageBucket(),
                    attemptStorageKey,
                    in,
                    content.contentLength(),
                    content.contentType()
            );
        } catch (Exception e) {
            recordDefinitivePutFailureIfMissing(uploadingSession, version.storageBucket(), attemptStorageKey, e);
            throw new IllegalStateException("failed to store object content", e);
        }

        ObjectStoreObject stored = objectStore.head(version.storageBucket(), attemptStorageKey)
                .orElseThrow(() -> new IllegalStateException(
                        "stored object metadata is not yet visible; recovery will retry"));
        validateSubmittedContentMetadata(content, stored);
        String etag = stored.etag();
        OssObjectVersion uploadedVersion = version.withUploadedContentAt(
                attemptStorageKey,
                content.contentType(),
                content.contentLength(),
                content.checksumSha256()
        );
        OssObjectVersion activatedVersion = uploadedVersion.activate(etag, now);
        OssObject activatedObject = object.activate(activatedVersion, now);
        OssUploadSession completedSession = uploadingSession.complete(now);

        transactionOperations.finalizeUpload(activatedVersion, activatedObject, completedSession);

        return toMetadataResult(activatedObject, activatedVersion);
    }

    public ObjectMetadataResult completeInternalUpload(
            String serviceSubject,
            CompleteObjectUploadCommand command
    ) {
        requireCompleteCommand(command);
        if (serviceSubject == null || serviceSubject.isBlank()) {
            throw objectNotFound();
        }
        String authenticatedService = serviceSubject.trim();
        OssUploadSession session = uploadSessionRepository.findById(command.sessionId())
                .orElseThrow(this::objectNotFound);
        if (!session.ownerService().equals(authenticatedService)
                || "USER".equalsIgnoreCase(session.ownerType())
                || !session.objectId().equals(command.objectId())
                || !session.versionId().equals(command.versionId())
                || session.createdBy() == null
                || session.createdBy().isBlank()) {
            throw objectNotFound();
        }
        OssObject object = objectRepository.findById(command.objectId())
                .orElseThrow(this::objectNotFound);
        if (!object.ownerService().equals(authenticatedService)
                || "USER".equalsIgnoreCase(object.ownerType())) {
            throw objectNotFound();
        }
        return completeUpload(new CompleteObjectUploadCommand(
                command.sessionId(),
                command.objectId(),
                command.versionId(),
                command.content(),
                session.createdBy()
        ), session);
    }

    private void recordDefinitivePutFailureIfMissing(
            OssUploadSession uploadingSession,
            String bucket,
            String attemptStorageKey,
            Exception failure
    ) {
        Optional<ObjectStoreObject> stored;
        try {
            stored = objectStore.head(bucket, attemptStorageKey);
        } catch (RuntimeException headFailure) {
            failure.addSuppressed(headFailure);
            return;
        }
        if (stored.isPresent()) {
            return;
        }
        OssUploadSession failedClaim = uploadingSession.recordPutFailure(
                clock.instant(), describePutFailure(failure));
        transactionOperations.recordCompletionFailure(failedClaim);
    }

    private static String describePutFailure(Exception failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        String detail = failure.getClass().getSimpleName() + ":" + message
                .replace('\n', ' ')
                .replace('\r', ' ');
        return detail.length() <= 500 ? detail : detail.substring(0, 500);
    }

    static String attemptStorageKey(OssUploadSession session) {
        String baseStorageKey = "objects/" + session.objectId() + "/"
                + session.versionId() + "/" + session.expectedFileName();
        if (session.claimVersion() <= 0L) {
            return baseStorageKey;
        }
        return baseStorageKey + ".claim-" + session.claimVersion();
    }

    private ObjectMetadataResult canonicalMetadata(UUID objectId, UUID versionId) {
        UploadTarget target = loadUploadTarget(objectId, versionId);
        OssObject object = target.object();
        OssObjectVersion version = target.version();
        if (!Objects.equals(object.currentVersionId(), version.versionId())) {
            throw objectNotFound();
        }
        return toMetadataResult(object, version);
    }

    private UploadTarget loadUploadTarget(UUID objectId, UUID versionId) {
        OssObject object = objectRepository.findById(objectId)
                .orElseThrow(this::objectNotFound);
        OssObjectVersion version = versionRepository.findById(versionId)
                .orElseThrow(this::objectNotFound);
        if (!object.objectId().equals(version.objectId())) {
            throw objectNotFound();
        }
        return new UploadTarget(object, version);
    }

    private ObjectUploadSessionResult toUploadSessionResult(OssUploadSession session) {
        return new ObjectUploadSessionResult(
                session.sessionId(),
                session.objectId(),
                session.versionId(),
                session.uploadMode(),
                "/api/oss/objects/" + session.objectId() + "/complete",
                session.expiresAt()
        );
    }

    private void validatePrepareReplay(
            PrepareObjectUploadCommand command,
            String safeFileName,
            OssVisibility visibility,
            OssUploadSession session
    ) {
        OssObject object = objectRepository.findById(session.objectId()).orElse(null);
        boolean matches = object != null
                && Objects.equals(object.usage(), requireText(command.usage(), "usage"))
                && Objects.equals(object.ownerService(), requireText(command.ownerService(), "ownerService"))
                && Objects.equals(object.ownerDomain(), requireText(command.ownerDomain(), "ownerDomain"))
                && Objects.equals(object.ownerType(), requireText(command.ownerType(), "ownerType"))
                && Objects.equals(object.ownerId(), requireText(command.ownerId(), "ownerId"))
                && object.visibility() == visibility
                && Objects.equals(session.ownerService(), requireText(command.ownerService(), "ownerService"))
                && Objects.equals(session.ownerDomain(), requireText(command.ownerDomain(), "ownerDomain"))
                && Objects.equals(session.ownerType(), requireText(command.ownerType(), "ownerType"))
                && Objects.equals(session.ownerId(), requireText(command.ownerId(), "ownerId"))
                && Objects.equals(session.expectedFileName(), safeFileName)
                && Objects.equals(session.expectedContentType(), normalizeContentType(command.contentType()))
                && session.expectedContentLength() == Math.max(0L, command.contentLength())
                && Objects.equals(session.expectedChecksumSha256(), normalize(command.checksumSha256()))
                && Objects.equals(session.createdBy(), normalize(command.actorId()));
        if (!matches) {
            throw new IllegalArgumentException("prepare request id conflict");
        }
    }

    private void requireInternalPrepareReplayOwner(
            OssUploadSession session,
            String serviceSubject
    ) {
        OssObject object = objectRepository.findById(session.objectId()).orElseThrow(this::objectNotFound);
        if (!serviceSubject.equals(session.ownerService())
                || "USER".equalsIgnoreCase(session.ownerType())
                || !serviceSubject.equals(object.ownerService())
                || "USER".equalsIgnoreCase(object.ownerType())) {
            throw objectNotFound();
        }
    }

    private UUID deterministicId(UUID requestId, String scope) {
        return UUID.nameUUIDFromBytes(
                ("community-oss:" + scope + ":" + requestId).getBytes(StandardCharsets.UTF_8));
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

    private static void validateSubmittedContentMetadata(
            ObjectUploadContent content,
            ObjectStoreObject metadata
    ) {
        if (metadata.contentLength() != content.contentLength()) {
            throw new IllegalStateException(
                    "stored content length does not match submitted content: expected "
                            + content.contentLength() + ", actual " + metadata.contentLength());
        }
        if (!content.contentType().equals(metadata.contentType())) {
            throw new IllegalStateException(
                    "stored content type does not match submitted content: expected "
                            + content.contentType() + ", actual " + metadata.contentType());
        }
    }

    static void validateStoredMetadata(
            OssUploadSession session,
            ObjectStoreObject metadata
    ) {
        if (session.expectedContentLength() > 0
                && metadata.contentLength() != session.expectedContentLength()) {
            throw new IllegalStateException(
                    "stored content length does not match the upload claim: expected "
                            + session.expectedContentLength() + ", actual " + metadata.contentLength());
        }
        if (!"application/octet-stream".equals(session.expectedContentType())
                && !session.expectedContentType().equals(metadata.contentType())) {
            throw new IllegalStateException(
                    "stored content type does not match the upload claim: expected "
                            + session.expectedContentType() + ", actual " + metadata.contentType());
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

    private void requireCompleteCommand(CompleteObjectUploadCommand command) {
        if (command == null || command.sessionId() == null
                || command.objectId() == null || command.versionId() == null
                || command.content() == null || command.actorId() == null
                || command.actorId().isBlank()) {
            throw new IllegalArgumentException("upload command is incomplete");
        }
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

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeContentType(String value) {
        return value == null || value.isBlank() ? "application/octet-stream" : value.trim();
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null || value.isBlank() ? "http://localhost:18090" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private BusinessException objectNotFound() {
        return new BusinessException(CommonErrorCode.NOT_FOUND, "OSS object not found");
    }

    private record UploadTarget(OssObject object, OssObjectVersion version) {
    }
}
