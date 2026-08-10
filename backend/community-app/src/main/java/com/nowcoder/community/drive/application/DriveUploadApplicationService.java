package com.nowcoder.community.drive.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.drive.application.command.DriveUploadContent;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.model.DriveUpload;
import com.nowcoder.community.drive.domain.model.DriveUploadStatus;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.drive.domain.repository.DriveUploadRepository;
import com.nowcoder.community.drive.domain.service.DriveEntryDomainService;
import com.nowcoder.community.drive.exception.DriveErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class DriveUploadApplicationService {

    private static final String USAGE = "DRIVE_FILE";
    private static final String OWNER_SERVICE = "community-app";
    private static final String OWNER_DOMAIN = "drive";
    private static final String OWNER_TYPE_UPLOAD = "drive-upload";
    private static final String VISIBILITY_PRIVATE = "PRIVATE";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String UPLOAD_METHOD = "POST";
    private static final String FILE_FIELD = "file";
    private static final String OSS_STATUS_ACTIVE = "ACTIVE";
    private static final String OSS_STATUS_PURGED = "PURGED";
    private static final long PREPARATION_TTL_SECONDS = 900L;

    private final DriveSpaceRepository spaceRepository;
    private final DriveEntryRepository entryRepository;
    private final DriveUploadRepository uploadRepository;
    private final DriveObjectStoragePort objectStoragePort;
    private final Clock clock;
    private final DriveTransactionOperations transactionOperations;
    private final UuidV7Generator idGenerator;
    private final DriveEntryDomainService entryDomainService = new DriveEntryDomainService();

    public DriveUploadApplicationService(
            DriveSpaceRepository spaceRepository,
            DriveEntryRepository entryRepository,
            DriveUploadRepository uploadRepository,
            DriveObjectStoragePort objectStoragePort,
            Clock clock,
            DriveTransactionOperations transactionOperations,
            UuidV7Generator idGenerator
    ) {
        this.spaceRepository = Objects.requireNonNull(spaceRepository, "spaceRepository must not be null");
        this.entryRepository = Objects.requireNonNull(entryRepository, "entryRepository must not be null");
        this.uploadRepository = Objects.requireNonNull(uploadRepository, "uploadRepository must not be null");
        this.objectStoragePort = Objects.requireNonNull(objectStoragePort, "objectStoragePort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.transactionOperations = Objects.requireNonNull(
                transactionOperations,
                "transactionOperations must not be null"
        );
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
    }

    public UploadSessionResult prepareUpload(PrepareUploadCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requirePrepareCommand(command);
        DriveUpload preparing = transactionOperations.requiresNew(() -> createPreparingUpload(command));
        DriveObjectStoragePort.PreparedObject prepared;
        try {
            prepared = prepareObject(preparing);
        } catch (RuntimeException e) {
            throw new BusinessException(DriveErrorCode.DRIVE_STORAGE_UNAVAILABLE, "网盘存储服务不可用", e);
        }
        DriveUpload upload = persistPreparedUpload(preparing.uploadId(), prepared);
        DriveSpace space = loadSpace(upload.spaceId());
        return toUploadSession(upload, space);
    }

    private DriveUpload createPreparingUpload(PrepareUploadCommand command) {
        UUID actorUserId = command.actorUserId();
        Instant now = clock.instant();
        DriveSpace space = loadOrCreateSpace(actorUserId, now);
        validateParent(command.parentId(), space.spaceId());
        String name = normalizeName(command.fileName());
        rejectDuplicate(space.spaceId(), command.parentId(), name);
        long contentLength = requireContentLength(command.contentLength());
        if (contentLength > space.remainingBytes()) {
            throw new BusinessException(DriveErrorCode.DRIVE_QUOTA_EXCEEDED, "网盘容量不足");
        }
        DriveUpload upload = DriveUpload.preparing(
                idGenerator.next(),
                space.spaceId(),
                command.parentId(),
                name,
                contentLength,
                normalizeContentType(command.contentType()),
                normalize(command.checksumSha256()),
                actorUserId,
                now,
                now.plusSeconds(PREPARATION_TTL_SECONDS)
        );
        uploadRepository.save(upload);
        return upload;
    }

    private DriveObjectStoragePort.PreparedObject prepareObject(DriveUpload upload) {
        DriveObjectStoragePort.PreparedObject prepared = objectStoragePort.prepareUpload(new DriveObjectStoragePort.PrepareObject(
                upload.uploadId(),
                USAGE,
                OWNER_SERVICE,
                OWNER_DOMAIN,
                OWNER_TYPE_UPLOAD,
                upload.uploadId().toString(),
                VISIBILITY_PRIVATE,
                upload.name(),
                upload.mimeType(),
                upload.sizeBytes(),
                upload.checksumSha256(),
                upload.createdBy().toString()
        ));
        validatePreparedObject(prepared);
        return prepared;
    }

    private DriveUpload persistPreparedUpload(UUID uploadId, DriveObjectStoragePort.PreparedObject prepared) {
        return transactionOperations.requiresNew(() -> persistPreparedUploadInCurrentTransaction(uploadId, prepared));
    }

    private DriveUpload persistPreparedUploadInCurrentTransaction(
            UUID uploadId,
            DriveObjectStoragePort.PreparedObject prepared
    ) {
        DriveUpload current = loadUpload(uploadId);
        if (current.matchesPrepared(prepared.objectId(), prepared.versionId(), prepared.sessionId(), prepared.expiresAt())) {
            return current;
        }
        DriveUpload next = current.markPrepared(
                prepared.objectId(),
                prepared.versionId(),
                prepared.sessionId(),
                prepared.expiresAt(),
                clock.instant()
        );
        if (uploadRepository.transitionStatus(next, DriveUploadStatus.PREPARING)) {
            return next;
        }
        DriveUpload latest = loadUpload(uploadId);
        if (latest.matchesPrepared(prepared.objectId(), prepared.versionId(), prepared.sessionId(), prepared.expiresAt())) {
            return latest;
        }
        throw new BusinessException(DriveErrorCode.DRIVE_UPLOAD_INVALID, "上传会话不可用");
    }

    private DriveSpace loadOrCreateSpace(UUID actorUserId, Instant now) {
        UUID userId = requireUser(actorUserId);
        return spaceRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSpace(userId, now));
    }

    private DriveSpace createDefaultSpace(UUID userId, Instant now) {
        DriveSpace space = DriveSpace.createDefault(idGenerator.next(), userId, now);
        DriveSpaceRepository.CreateResult result = spaceRepository.create(space);
        if (result != null
                && (result.status() == DriveSpaceRepository.CreateStatus.CREATED
                || result.status() == DriveSpaceRepository.CreateStatus.ALREADY_EXISTS)
                && result.space() != null) {
            return result.space();
        }
        throw new BusinessException(INTERNAL_ERROR, "网盘空间创建失败");
    }

    public DriveEntryResult completeUpload(CompleteUploadCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireCompleteCommand(command);
        UUID actorUserId = requireUser(command.actorUserId());
        DriveUploadContent content = command.content();

        CompletionClaim claim = claimUploadForCompletion(command.uploadId(), actorUserId, content);
        DriveUpload claimed = claim.upload();
        if (claimed.status() == DriveUploadStatus.COMPLETED) {
            return completedEntryResult(claimed);
        }
        if (claimed.status() == DriveUploadStatus.OBJECT_COMPLETED) {
            return finalizeObjectCompletedUpload(claimed.uploadId(), actorUserId);
        }
        if (claimed.status() == DriveUploadStatus.EXPIRED
                || claimed.status() == DriveUploadStatus.FAILED
                || claimed.status() == DriveUploadStatus.CLEANUP_PENDING) {
            throw new BusinessException(DriveErrorCode.DRIVE_UPLOAD_INVALID, "上传会话不可用");
        }
        if (claimed.status() != DriveUploadStatus.COMPLETING) {
            throw new BusinessException(DriveErrorCode.DRIVE_UPLOAD_INVALID, "上传会话不可用");
        }
        if (!claim.owned()) {
            throw new BusinessException(DriveErrorCode.DRIVE_UPLOAD_INVALID, "上传会话处理中");
        }

        long actualContentLength = requireContentLength(content.contentLength());
        try {
            objectStoragePort.completeUpload(new DriveObjectStoragePort.CompleteObject(
                    claimed.ossSessionId(),
                    claimed.objectId(),
                    claimed.versionId(),
                    claimed.name(),
                    normalizeContentType(content.contentType()),
                    actualContentLength,
                    claimed.checksumSha256(),
                    content
            ));
        } catch (RuntimeException e) {
            if (storageCompletionState(claimed) == StorageCompletionState.COMPLETED) {
                markObjectCompleted(claimed.uploadId());
            }
            throw new BusinessException(DriveErrorCode.DRIVE_STORAGE_UNAVAILABLE, "网盘存储服务不可用", e);
        }

        DriveUpload objectCompleted = markObjectCompleted(claimed.uploadId());
        if (objectCompleted.status() == DriveUploadStatus.COMPLETED) {
            return completedEntryResult(objectCompleted);
        }
        return finalizeObjectCompletedUpload(objectCompleted.uploadId(), actorUserId);
    }

    public RecoveryResult recoverStaleUploads(Instant updatedBefore, int limit) {
        if (updatedBefore == null || limit <= 0) {
            return new RecoveryResult(0, 0, 0, 0);
        }
        Instant now = clock.instant();
        int prepared = 0;
        int finalized = 0;
        int markedObjectCompleted = 0;
        int failed = 0;
        int skipped = 0;
        for (DriveUpload upload : uploadRepository.listRecoverableBefore(updatedBefore, limit)) {
            if (!uploadRepository.recordRecoveryAttempt(upload.uploadId(), upload.status(), now)) {
                skipped++;
                continue;
            }
            if (upload.status() == DriveUploadStatus.PREPARING) {
                if (upload.expiredAt(now)) {
                    expirePreparingUpload(upload.uploadId());
                    failed++;
                    continue;
                }
                try {
                    DriveObjectStoragePort.PreparedObject remote = prepareObject(upload);
                    persistPreparedUpload(upload.uploadId(), remote);
                    prepared++;
                } catch (RuntimeException ignored) {
                    skipped++;
                }
                continue;
            }
            if (upload.status() == DriveUploadStatus.CLEANUP_PENDING) {
                if (cleanupPendingUpload(upload.uploadId(), upload.createdBy())) {
                    failed++;
                } else {
                    skipped++;
                }
                continue;
            }
            if (upload.status() == DriveUploadStatus.OBJECT_COMPLETED) {
                try {
                    finalizeObjectCompletedUpload(upload.uploadId(), upload.createdBy());
                    finalized++;
                } catch (RuntimeException e) {
                    DriveUpload latest = uploadRepository.findById(upload.uploadId()).orElse(upload);
                    if (latest.status() == DriveUploadStatus.FAILED) {
                        failed++;
                    } else if (latest.status() == DriveUploadStatus.CLEANUP_PENDING) {
                        skipped++;
                    } else {
                        skipped++;
                    }
                }
                continue;
            }
            if (upload.status() == DriveUploadStatus.COMPLETING) {
                StorageCompletionState storageState = storageCompletionState(upload);
                if (storageState == StorageCompletionState.COMPLETED) {
                    CompletionRecovery recovery = recoverCompletedUpload(upload);
                    finalized += recovery.finalized();
                    markedObjectCompleted += recovery.markedObjectCompleted();
                    failed += recovery.failed();
                    skipped += recovery.skipped();
                } else if (storageState == StorageCompletionState.NOT_COMPLETED || upload.expiredAt(now)) {
                    DriveObjectStoragePort.UploadCancellation cancellation = cancelUpload(upload);
                    if (cancellation == null) {
                        skipped++;
                    } else if (cancellation.completed()) {
                        CompletionRecovery recovery = recoverCompletedUpload(upload);
                        finalized += recovery.finalized();
                        markedObjectCompleted += recovery.markedObjectCompleted();
                        failed += recovery.failed();
                        skipped += recovery.skipped();
                    } else if (cancellation.cancelled()
                            && beginUploadCleanup(upload.uploadId(), DriveUploadStatus.COMPLETING, now)
                            && cleanupPendingUpload(upload.uploadId(), upload.createdBy())) {
                        failed++;
                    } else {
                        skipped++;
                    }
                } else {
                    skipped++;
                }
                continue;
            }
            skipped++;
        }
        return new RecoveryResult(prepared, finalized, markedObjectCompleted, failed, skipped);
    }

    private void expirePreparingUpload(UUID uploadId) {
        transactionOperations.requiresNew(() -> {
            DriveUpload current = loadUpload(uploadId);
            if (current.status() != DriveUploadStatus.PREPARING) {
                return;
            }
            uploadRepository.transitionStatus(current.expirePreparation(clock.instant()), DriveUploadStatus.PREPARING);
        });
    }

    private CompletionClaim claimUploadForCompletion(UUID uploadId, UUID actorUserId, DriveUploadContent content) {
        return runInCompletionTransaction(() -> {
            Instant now = clock.instant();
            DriveUpload upload = loadUpload(uploadId);
            DriveSpace space = loadSpace(upload.spaceId());
            lockSpace(space.spaceId());
            ensureUploadOwner(upload, space, actorUserId);
            if (upload.status() == DriveUploadStatus.COMPLETED || upload.status() == DriveUploadStatus.OBJECT_COMPLETED) {
                return new CompletionClaim(upload, false);
            }
            if (upload.status() == DriveUploadStatus.COMPLETING) {
                return new CompletionClaim(upload, false);
            }
            if (upload.expiredAt(now)) {
                DriveUpload expired = upload.complete(idGenerator.next(), now);
                uploadRepository.save(expired);
                return new CompletionClaim(expired, false);
            }
            if (upload.status() != DriveUploadStatus.PREPARED) {
                return new CompletionClaim(upload, false);
            }

            long actualContentLength = requireContentLength(content.contentLength());
            if (actualContentLength != upload.sizeBytes()) {
                throw new BusinessException(INVALID_ARGUMENT, "上传文件大小不匹配");
            }
            validateParent(upload.parentId(), upload.spaceId());
            rejectDuplicate(upload.spaceId(), upload.parentId(), upload.name());
            if (upload.sizeBytes() > space.remainingBytes()) {
                throw new BusinessException(DriveErrorCode.DRIVE_QUOTA_EXCEEDED, "网盘容量不足");
            }
            if (!spaceRepository.reserve(space.spaceId(), upload.sizeBytes(), now)) {
                throw new BusinessException(DriveErrorCode.DRIVE_QUOTA_EXCEEDED, "网盘容量不足");
            }

            DriveUpload completing = upload.startCompleting(idGenerator.next(), now);
            if (uploadRepository.transitionStatus(completing, DriveUploadStatus.PREPARED)) {
                return new CompletionClaim(completing, true);
            }
            spaceRepository.releaseReserved(space.spaceId(), upload.sizeBytes(), now);
            return new CompletionClaim(loadUpload(upload.uploadId()), false);
        });
    }

    private DriveUpload markObjectCompleted(UUID uploadId) {
        return runInCompletionTransaction(() -> {
            Instant now = clock.instant();
            DriveUpload upload = loadUpload(uploadId);
            if (upload.status() == DriveUploadStatus.OBJECT_COMPLETED || upload.status() == DriveUploadStatus.COMPLETED) {
                return upload;
            }
            if (upload.status() != DriveUploadStatus.COMPLETING) {
                throw new BusinessException(DriveErrorCode.DRIVE_UPLOAD_INVALID, "上传会话不可用");
            }
            DriveUpload objectCompleted = upload.markObjectCompleted(now);
            if (uploadRepository.transitionStatus(objectCompleted, DriveUploadStatus.COMPLETING)) {
                return objectCompleted;
            }
            return loadUpload(upload.uploadId());
        });
    }

    private CompletionRecovery recoverCompletedUpload(DriveUpload upload) {
        int markedObjectCompleted = 0;
        try {
            DriveUpload objectCompleted = markObjectCompleted(upload.uploadId());
            if (objectCompleted.status() != DriveUploadStatus.OBJECT_COMPLETED) {
                return new CompletionRecovery(0, 0, 0, 1);
            }
            markedObjectCompleted = 1;
            finalizeObjectCompletedUpload(objectCompleted.uploadId(), objectCompleted.createdBy());
            return new CompletionRecovery(1, markedObjectCompleted, 0, 0);
        } catch (RuntimeException e) {
            DriveUpload latest = uploadRepository.findById(upload.uploadId()).orElse(upload);
            if (latest.status() == DriveUploadStatus.FAILED) {
                return new CompletionRecovery(0, markedObjectCompleted, 1, 0);
            }
            return new CompletionRecovery(0, markedObjectCompleted, 0, 1);
        }
    }

    private StorageCompletionState storageCompletionState(DriveUpload upload) {
        DriveObjectStoragePort.ObjectMetadata metadata;
        try {
            metadata = objectStoragePort.getMetadata(upload.objectId());
        } catch (RuntimeException e) {
            return StorageCompletionState.UNKNOWN;
        }
        if (metadata == null || !upload.objectId().equals(metadata.objectId())) {
            return StorageCompletionState.UNKNOWN;
        }
        if (upload.versionId().equals(metadata.currentVersionId()) && OSS_STATUS_ACTIVE.equalsIgnoreCase(metadata.status())) {
            return StorageCompletionState.COMPLETED;
        }
        if (OSS_STATUS_PURGED.equalsIgnoreCase(metadata.status())) {
            return StorageCompletionState.NOT_COMPLETED;
        }
        return StorageCompletionState.UNKNOWN;
    }

    private DriveObjectStoragePort.UploadCancellation cancelUpload(DriveUpload upload) {
        try {
            return objectStoragePort.cancelUpload(
                    upload.ossSessionId(),
                    upload.objectId(),
                    upload.versionId()
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean beginUploadCleanup(UUID uploadId, DriveUploadStatus expectedStatus, Instant now) {
        return runInCompletionTransaction(() -> {
            DriveUpload upload = loadUpload(uploadId);
            if (upload.status() != expectedStatus) {
                return false;
            }
            DriveSpace space = loadSpace(upload.spaceId());
            lockSpace(space.spaceId());
            DriveUpload cleanupPending = upload.startCleanup(now);
            if (uploadRepository.transitionStatus(cleanupPending, expectedStatus)) {
                spaceRepository.releaseReserved(upload.spaceId(), upload.sizeBytes(), now);
                return true;
            }
            return false;
        });
    }

    private boolean cleanupPendingUpload(UUID uploadId, UUID actorUserId) {
        DriveUpload upload = loadUpload(uploadId);
        if (upload.status() != DriveUploadStatus.CLEANUP_PENDING) {
            return false;
        }
        try {
            objectStoragePort.deleteObject(upload.objectId(), actorUserId == null ? "" : actorUserId.toString());
        } catch (RuntimeException ignored) {
            return false;
        }
        return runInCompletionTransaction(() -> {
            DriveUpload current = loadUpload(uploadId);
            if (current.status() != DriveUploadStatus.CLEANUP_PENDING) {
                return false;
            }
            return uploadRepository.transitionStatus(
                    current.completeCleanup(clock.instant()),
                    DriveUploadStatus.CLEANUP_PENDING
            );
        });
    }

    private DriveEntryResult finalizeObjectCompletedUpload(UUID uploadId, UUID actorUserId) {
        try {
            return runInCompletionTransaction(() -> {
                Instant now = clock.instant();
                DriveUpload upload = loadUpload(uploadId);
                DriveSpace space = loadSpace(upload.spaceId());
                lockSpace(space.spaceId());
                ensureUploadOwner(upload, space, actorUserId);
                if (upload.status() == DriveUploadStatus.COMPLETED) {
                    return completedEntryResult(upload);
                }
                if (upload.status() != DriveUploadStatus.OBJECT_COMPLETED) {
                    throw new BusinessException(DriveErrorCode.DRIVE_UPLOAD_INVALID, "上传会话不可用");
                }
                UUID entryId = upload.completedEntryId();
                if (entryId == null) {
                    throw new BusinessException(DriveErrorCode.DRIVE_UPLOAD_INVALID, "上传会话不可用");
                }
                try {
                    validateParent(upload.parentId(), upload.spaceId());
                    rejectDuplicate(upload.spaceId(), upload.parentId(), upload.name());
                } catch (BusinessException e) {
                    throw new TerminalObjectCompletedException(upload, now, e);
                }

                DriveEntry entry = DriveEntry.file(
                        entryId,
                        upload.spaceId(),
                        upload.parentId(),
                        upload.name(),
                        upload.objectId(),
                        upload.versionId(),
                        upload.sizeBytes(),
                        upload.mimeType(),
                        now
                );
                DriveEntryRepository.CreateResult createResult = entryRepository.create(entry);
                if (createResult == null || createResult.status() == DriveEntryRepository.CreateStatus.CONFLICT) {
                    throw new BusinessException(INTERNAL_ERROR, "网盘条目创建失败");
                }
                if (createResult.status() == DriveEntryRepository.CreateStatus.ACTIVE_NAME_CONFLICT) {
                    throw new TerminalObjectCompletedException(
                            upload,
                            now,
                            new BusinessException(DriveErrorCode.DRIVE_DUPLICATE_NAME, "同名文件或文件夹已存在")
                    );
                }
                if (!spaceRepository.commitReserved(space.spaceId(), upload.sizeBytes(), now)) {
                    throw new TerminalObjectCompletedException(
                            upload,
                            now,
                            new BusinessException(DriveErrorCode.DRIVE_QUOTA_EXCEEDED, "网盘容量不足")
                    );
                }
                DriveUpload completed = upload.completeFinalization(now);
                if (!uploadRepository.transitionStatus(completed, DriveUploadStatus.OBJECT_COMPLETED)) {
                    DriveUpload latest = loadUpload(upload.uploadId());
                    if (latest.status() == DriveUploadStatus.COMPLETED) {
                        return completedEntryResult(latest);
                    }
                    throw new BusinessException(DriveErrorCode.DRIVE_UPLOAD_INVALID, "上传会话不可用");
                }
                return toEntryResult(entry);
            });
        } catch (TerminalObjectCompletedException e) {
            if (beginUploadCleanup(e.upload().uploadId(), DriveUploadStatus.OBJECT_COMPLETED, e.failedAt())) {
                cleanupPendingUpload(e.upload().uploadId(), actorUserId);
            } else {
                DriveUpload latest = loadUpload(e.upload().uploadId());
                if (latest.status() == DriveUploadStatus.COMPLETED) {
                    return completedEntryResult(latest);
                }
            }
            throw e.cause();
        }
    }

    private static final class TerminalObjectCompletedException extends RuntimeException {

        private final DriveUpload upload;
        private final Instant failedAt;
        private final BusinessException cause;

        private TerminalObjectCompletedException(DriveUpload upload, Instant failedAt, BusinessException cause) {
            super(cause);
            this.upload = upload;
            this.failedAt = failedAt;
            this.cause = cause;
        }

        private DriveUpload upload() {
            return upload;
        }

        private Instant failedAt() {
            return failedAt;
        }

        private BusinessException cause() {
            return cause;
        }
    }

    private DriveUpload loadUpload(UUID uploadId) {
        return uploadRepository.findById(uploadId)
                .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_UPLOAD_INVALID, "上传会话不可用"));
    }

    private DriveSpace loadSpace(UUID spaceId) {
        return spaceRepository.findById(spaceId)
                .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_SPACE_NOT_FOUND, "网盘空间不存在"));
    }

    private void ensureUploadOwner(DriveUpload upload, DriveSpace space, UUID actorUserId) {
        if (!space.userId().equals(actorUserId) || !upload.createdBy().equals(actorUserId)) {
            throw new BusinessException(FORBIDDEN, "只能完成自己的上传会话");
        }
    }

    private void lockSpace(UUID spaceId) {
        if (spaceRepository.lockById(spaceId) == null) {
            throw new BusinessException(DriveErrorCode.DRIVE_SPACE_NOT_FOUND, "网盘空间不存在");
        }
    }

    private DriveEntryResult completedEntryResult(DriveUpload upload) {
        UUID entryId = upload.completedEntryId();
        if (entryId == null) {
            throw new BusinessException(DriveErrorCode.DRIVE_UPLOAD_INVALID, "上传会话不可用");
        }
        return entryRepository.findById(upload.spaceId(), entryId)
                .map(this::toEntryResult)
                .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_ENTRY_NOT_FOUND, "网盘条目不存在"));
    }

    private <T> T runInCompletionTransaction(Supplier<T> action) {
        return transactionOperations.requiresNew(action);
    }

    private record CompletionClaim(DriveUpload upload, boolean owned) {
    }

    private record CompletionRecovery(int finalized, int markedObjectCompleted, int failed, int skipped) {
    }

    private enum StorageCompletionState {
        COMPLETED,
        NOT_COMPLETED,
        UNKNOWN
    }

    private void validateParent(UUID parentId, UUID spaceId) {
        if (parentId == null) {
            return;
        }
        DriveEntry parent = entryRepository.findById(spaceId, parentId)
                .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_PARENT_NOT_FOUND, "目标文件夹不存在"));
        if (!parent.folder() || parent.status() != DriveEntryStatus.ACTIVE) {
            throw new BusinessException(DriveErrorCode.DRIVE_PARENT_NOT_FOUND, "目标文件夹不存在");
        }
    }

    private void rejectDuplicate(UUID spaceId, UUID parentId, String name) {
        if (entryRepository.findActiveChildByName(spaceId, parentId, name).isPresent()) {
            throw new BusinessException(DriveErrorCode.DRIVE_DUPLICATE_NAME, "同名文件或文件夹已存在");
        }
    }

    private UploadSessionResult toUploadSession(DriveUpload upload, DriveSpace space) {
        String fileKey = fileKey(upload.uploadId(), upload.name());
        return new UploadSessionResult(
                upload.uploadId().toString(),
                fileKey,
                new UploadSessionResult.UploadInstruction(
                        "/api/drive/uploads/" + upload.uploadId() + "/complete",
                        UPLOAD_METHOD,
                        FILE_FIELD,
                        Map.of("fileKey", fileKey),
                        Map.of()
                ),
                new UploadSessionResult.UploadConstraints(space.quotaBytes(), List.of()),
                upload.expiresAt()
        );
    }

    private DriveEntryResult toEntryResult(DriveEntry entry) {
        return new DriveEntryResult(
                entry.entryId(),
                entry.parentId(),
                entry.type().name(),
                entry.name(),
                entry.sizeBytes(),
                entry.mimeType(),
                entry.status().name(),
                entry.updatedAt()
        );
    }

    private String normalizeName(String fileName) {
        try {
            return entryDomainService.normalizeName(fileName);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(INVALID_ARGUMENT, e.getMessage());
        }
    }

    private static String fileKey(UUID uploadId, String name) {
        return "drive/" + uploadId + "/" + name;
    }

    private static void requirePrepareCommand(PrepareUploadCommand command) {
        requireUser(command.actorUserId());
    }

    private static void requireCompleteCommand(CompleteUploadCommand command) {
        if (command.uploadId() == null || command.content() == null || command.content().uploadStream() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "上传参数非法");
        }
    }

    private static UUID requireUser(UUID actorUserId) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        return actorUserId;
    }

    private static long requireContentLength(long contentLength) {
        if (contentLength < 0) {
            throw new BusinessException(INVALID_ARGUMENT, "文件大小非法");
        }
        return contentLength;
    }

    private static String normalizeContentType(String contentType) {
        String value = normalize(contentType);
        return value.isBlank() ? DEFAULT_CONTENT_TYPE : value;
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").trim();
    }

    private static void validatePreparedObject(DriveObjectStoragePort.PreparedObject prepared) {
        if (prepared == null || prepared.sessionId() == null || prepared.objectId() == null
                || prepared.versionId() == null || prepared.expiresAt() == null) {
            throw new BusinessException(INTERNAL_ERROR, "签发网盘上传参数失败");
        }
    }

    public record PrepareUploadCommand(
            UUID actorUserId,
            UUID parentId,
            String fileName,
            String contentType,
            long contentLength,
            String checksumSha256
    ) {
    }

    public record CompleteUploadCommand(UUID actorUserId, UUID uploadId, DriveUploadContent content) {
    }

    public record RecoveryResult(
            int prepared,
            int finalized,
            int markedObjectCompleted,
            int failed,
            int skipped
    ) {
        public RecoveryResult(int finalized, int markedObjectCompleted, int failed, int skipped) {
            this(0, finalized, markedObjectCompleted, failed, skipped);
        }
    }

    public record UploadSessionResult(
            String uploadId,
            String fileKey,
            UploadInstruction upload,
            UploadConstraints constraints,
            Instant expiresAt
    ) {
        public record UploadInstruction(
                String url,
                String method,
                String fileField,
                Map<String, String> fields,
                Map<String, String> headers
        ) {
            public UploadInstruction {
                fields = fields == null ? Map.of() : Map.copyOf(fields);
                headers = headers == null ? Map.of() : Map.copyOf(headers);
            }
        }

        public record UploadConstraints(long maxBytes, List<String> mimeTypes) {
            public UploadConstraints {
                mimeTypes = mimeTypes == null ? List.of() : List.copyOf(mimeTypes);
            }
        }
    }
}
