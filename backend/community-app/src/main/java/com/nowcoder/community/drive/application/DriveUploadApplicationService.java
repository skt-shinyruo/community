package com.nowcoder.community.drive.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.drive.application.command.CompleteDriveUploadCommand;
import com.nowcoder.community.drive.application.command.DriveUploadContent;
import com.nowcoder.community.drive.application.command.PrepareDriveUploadCommand;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
import com.nowcoder.community.drive.application.result.DriveUploadSessionResult;
import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.model.DriveUpload;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.drive.domain.repository.DriveUploadRepository;
import com.nowcoder.community.drive.domain.service.DriveEntryDomainService;
import com.nowcoder.community.drive.exception.DriveErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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

    private final DriveSpaceRepository spaceRepository;
    private final DriveEntryRepository entryRepository;
    private final DriveUploadRepository uploadRepository;
    private final DriveObjectStoragePort objectStoragePort;
    private final Clock clock;
    private final TransactionTemplate expiredUploadTransactionTemplate;
    private final DriveEntryDomainService entryDomainService = new DriveEntryDomainService();

    @Autowired
    public DriveUploadApplicationService(
            DriveSpaceRepository spaceRepository,
            DriveEntryRepository entryRepository,
            DriveUploadRepository uploadRepository,
            DriveObjectStoragePort objectStoragePort,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.spaceRepository = spaceRepository;
        this.entryRepository = entryRepository;
        this.uploadRepository = uploadRepository;
        this.objectStoragePort = objectStoragePort;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        if (transactionManager == null) {
            this.expiredUploadTransactionTemplate = null;
        } else {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            this.expiredUploadTransactionTemplate = transactionTemplate;
        }
    }

    DriveUploadApplicationService(
            DriveSpaceRepository spaceRepository,
            DriveEntryRepository entryRepository,
            DriveUploadRepository uploadRepository,
            DriveObjectStoragePort objectStoragePort,
            Clock clock
    ) {
        this(spaceRepository, entryRepository, uploadRepository, objectStoragePort, clock, null);
    }

    @Transactional
    public DriveUploadSessionResult prepareUpload(PrepareDriveUploadCommand command) {
        requirePrepareCommand(command);
        UUID actorUserId = requireUser(command.actorUserId());
        Instant now = clock.instant();
        DriveSpace space = spaceRepository.findByUserId(actorUserId)
                .orElseGet(() -> DriveSpace.createDefault(UUID.randomUUID(), actorUserId, now));
        validateParent(command.parentId(), space.spaceId());
        String name = normalizeName(command.fileName());
        rejectDuplicate(space.spaceId(), command.parentId(), name);
        long contentLength = requireContentLength(command.contentLength());
        if (contentLength > space.remainingBytes()) {
            throw new BusinessException(DriveErrorCode.DRIVE_QUOTA_EXCEEDED, "网盘容量不足");
        }

        UUID uploadId = UUID.randomUUID();
        String contentType = normalizeContentType(command.contentType());
        String checksumSha256 = normalize(command.checksumSha256());
        DriveObjectStoragePort.PreparedObject prepared;
        try {
            prepared = objectStoragePort.prepareUpload(new DriveObjectStoragePort.PrepareObject(
                    USAGE,
                    OWNER_SERVICE,
                    OWNER_DOMAIN,
                    OWNER_TYPE_UPLOAD,
                    uploadId.toString(),
                    VISIBILITY_PRIVATE,
                    name,
                    contentType,
                    contentLength,
                    checksumSha256,
                    actorUserId.toString()
            ));
        } catch (RuntimeException e) {
            throw new BusinessException(DriveErrorCode.DRIVE_STORAGE_UNAVAILABLE, "网盘存储服务不可用", e);
        }
        validatePreparedObject(prepared);

        DriveUpload upload = DriveUpload.prepared(
                uploadId,
                space.spaceId(),
                command.parentId(),
                name,
                contentLength,
                contentType,
                prepared.objectId(),
                prepared.versionId(),
                prepared.sessionId(),
                actorUserId,
                now,
                prepared.expiresAt()
        );
        spaceRepository.save(space);
        uploadRepository.save(upload);
        return toUploadSession(upload, space);
    }

    @Transactional
    public DriveEntryResult completeUpload(CompleteDriveUploadCommand command) {
        requireCompleteCommand(command);
        UUID actorUserId = requireUser(command.actorUserId());
        DriveUpload upload = uploadRepository.findById(command.uploadId())
                .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_UPLOAD_INVALID, "上传会话不可用"));
        DriveSpace space = spaceRepository.findById(upload.spaceId())
                .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_SPACE_NOT_FOUND, "网盘空间不存在"));
        if (!space.userId().equals(actorUserId) || !upload.createdBy().equals(actorUserId)) {
            throw new BusinessException(FORBIDDEN, "只能完成自己的上传会话");
        }
        if (upload.completed()) {
            UUID entryId = upload.completedEntryId();
            if (entryId == null) {
                throw new BusinessException(DriveErrorCode.DRIVE_UPLOAD_INVALID, "上传会话不可用");
            }
            return entryRepository.findById(upload.spaceId(), entryId)
                    .map(this::toEntryResult)
                    .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_ENTRY_NOT_FOUND, "网盘条目不存在"));
        }

        Instant now = clock.instant();
        if (upload.expiredAt(now)) {
            persistExpiredUpload(upload.complete(UUID.randomUUID(), now));
            throw new BusinessException(DriveErrorCode.DRIVE_UPLOAD_INVALID, "上传会话不可用");
        }

        DriveUploadContent content = command.content();
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
        try {
            objectStoragePort.completeUpload(new DriveObjectStoragePort.CompleteObject(
                    upload.ossSessionId(),
                    upload.objectId(),
                    upload.versionId(),
                    upload.name(),
                    normalizeContentType(content.contentType()),
                    actualContentLength,
                    normalize(content.checksumSha256()),
                    content
            ));
        } catch (RuntimeException e) {
            throw new BusinessException(DriveErrorCode.DRIVE_STORAGE_UNAVAILABLE, "网盘存储服务不可用", e);
        }

        UUID entryId = UUID.randomUUID();
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
        DriveUpload completed = upload.complete(entryId, now);
        entryRepository.save(entry);
        uploadRepository.save(completed);
        return toEntryResult(entry);
    }

    private void persistExpiredUpload(DriveUpload expired) {
        if (expiredUploadTransactionTemplate == null) {
            uploadRepository.save(expired);
            return;
        }
        expiredUploadTransactionTemplate.executeWithoutResult(status -> uploadRepository.save(expired));
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

    private DriveUploadSessionResult toUploadSession(DriveUpload upload, DriveSpace space) {
        String fileKey = fileKey(upload.uploadId(), upload.name());
        return new DriveUploadSessionResult(
                upload.uploadId().toString(),
                fileKey,
                new DriveUploadSessionResult.UploadInstruction(
                        "/api/drive/uploads/" + upload.uploadId() + "/complete",
                        UPLOAD_METHOD,
                        FILE_FIELD,
                        Map.of("fileKey", fileKey),
                        Map.of()
                ),
                new DriveUploadSessionResult.UploadConstraints(space.quotaBytes(), List.of()),
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

    private static void requirePrepareCommand(PrepareDriveUploadCommand command) {
        if (command == null) {
            throw new BusinessException(INVALID_ARGUMENT, "上传参数非法");
        }
    }

    private static void requireCompleteCommand(CompleteDriveUploadCommand command) {
        if (command == null || command.uploadId() == null || command.content() == null || command.content().uploadStream() == null) {
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
}
