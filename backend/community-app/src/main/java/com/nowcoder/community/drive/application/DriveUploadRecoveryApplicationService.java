package com.nowcoder.community.drive.application;

import com.nowcoder.community.drive.application.DriveUploadApplicationService.StorageCompletionState;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.drive.domain.model.DriveUpload;
import com.nowcoder.community.drive.domain.model.DriveUploadStatus;
import com.nowcoder.community.drive.domain.repository.DriveUploadRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 失败 / 恢复链的 use case 入口：扫描 stale 上传，按状态推进 expire、cancel、cleanup 与补偿
 * finalize。完成链的状态机核心（markObjectCompleted、finalizeObjectCompletedUpload 及
 * cleanup 过渡）由 {@link DriveUploadApplicationService} 持有，这里通过同域互调复用（#214）。
 */
@Service
public class DriveUploadRecoveryApplicationService {

    private final DriveUploadApplicationService uploadApplicationService;
    private final DriveUploadRepository uploadRepository;
    private final DriveObjectStoragePort objectStoragePort;
    private final Clock clock;
    private final DriveTransactionOperations transactionOperations;

    public DriveUploadRecoveryApplicationService(
            DriveUploadApplicationService uploadApplicationService,
            DriveUploadRepository uploadRepository,
            DriveObjectStoragePort objectStoragePort,
            Clock clock,
            DriveTransactionOperations transactionOperations
    ) {
        this.uploadApplicationService = Objects.requireNonNull(
                uploadApplicationService,
                "uploadApplicationService must not be null"
        );
        this.uploadRepository = Objects.requireNonNull(uploadRepository, "uploadRepository must not be null");
        this.objectStoragePort = Objects.requireNonNull(objectStoragePort, "objectStoragePort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.transactionOperations = Objects.requireNonNull(
                transactionOperations,
                "transactionOperations must not be null"
        );
    }

    public RecoveryResult recoverStaleUploads(Instant updatedBefore, int limit) {
        if (updatedBefore == null || limit <= 0) {
            return new RecoveryResult(0, 0, 0, 0);
        }
        Instant now = clock.instant();
        RecoveryResult result = new RecoveryResult(0, 0, 0, 0);
        for (DriveUpload upload : uploadRepository.listRecoverableBefore(updatedBefore, limit)) {
            RecoveryOutcome outcome;
            try {
                outcome = recoverStaleUpload(upload, now);
            } catch (RuntimeException ignored) {
                outcome = RecoveryOutcome.SKIPPED;
            }
            result = add(result, outcome);
        }
        return result;
    }

    private RecoveryOutcome recoverStaleUpload(DriveUpload upload, Instant now) {
        if (!uploadRepository.recordRecoveryAttempt(upload.uploadId(), upload.status(), now)) {
            return RecoveryOutcome.SKIPPED;
        }
        return switch (upload.status()) {
            case PREPARING -> recoverPreparingUpload(upload, now);
            case COMPLETING -> recoverCompletingUpload(upload, now);
            case OBJECT_COMPLETED -> recoverObjectCompletedUpload(upload);
            case CLEANUP_PENDING -> uploadApplicationService.cleanupPendingUpload(upload.uploadId(), upload.createdBy())
                    ? RecoveryOutcome.FAILED
                    : RecoveryOutcome.SKIPPED;
            default -> RecoveryOutcome.SKIPPED;
        };
    }

    private RecoveryOutcome recoverPreparingUpload(DriveUpload upload, Instant now) {
        if (upload.expiredAt(now)) {
            expirePreparingUpload(upload.uploadId());
            return RecoveryOutcome.FAILED;
        }
        DriveObjectStoragePort.PreparedObject remote = uploadApplicationService.prepareObject(upload);
        uploadApplicationService.persistPreparedUpload(upload.uploadId(), remote);
        return RecoveryOutcome.PREPARED;
    }

    private RecoveryOutcome recoverObjectCompletedUpload(DriveUpload upload) {
        try {
            uploadApplicationService.finalizeObjectCompletedUpload(upload.uploadId(), upload.createdBy());
            return RecoveryOutcome.FINALIZED;
        } catch (RuntimeException e) {
            DriveUpload latest = uploadRepository.findById(upload.uploadId()).orElse(upload);
            return latest.status() == DriveUploadStatus.FAILED
                    ? RecoveryOutcome.FAILED
                    : RecoveryOutcome.SKIPPED;
        }
    }

    private RecoveryOutcome recoverCompletingUpload(DriveUpload upload, Instant now) {
        StorageCompletionState storageState = uploadApplicationService.storageCompletionState(upload);
        if (storageState == StorageCompletionState.COMPLETED) {
            return recoverCompletedUpload(upload);
        }
        if (storageState != StorageCompletionState.NOT_COMPLETED && !upload.expiredAt(now)) {
            return RecoveryOutcome.SKIPPED;
        }
        DriveObjectStoragePort.UploadCancellation cancellation = cancelUpload(upload);
        if (cancellation == null) {
            return RecoveryOutcome.SKIPPED;
        }
        if (cancellation.completed()) {
            return recoverCompletedUpload(upload);
        }
        if (cancellation.cancelled()
                && uploadApplicationService.beginUploadCleanup(upload.uploadId(), DriveUploadStatus.COMPLETING, now)
                && uploadApplicationService.cleanupPendingUpload(upload.uploadId(), upload.createdBy())) {
            return RecoveryOutcome.FAILED;
        }
        return RecoveryOutcome.SKIPPED;
    }

    private RecoveryOutcome recoverCompletedUpload(DriveUpload upload) {
        boolean markedObjectCompleted = false;
        try {
            DriveUpload objectCompleted = uploadApplicationService.markObjectCompleted(upload.uploadId());
            if (objectCompleted.status() != DriveUploadStatus.OBJECT_COMPLETED) {
                return RecoveryOutcome.SKIPPED;
            }
            markedObjectCompleted = true;
            uploadApplicationService.finalizeObjectCompletedUpload(objectCompleted.uploadId(), objectCompleted.createdBy());
            return RecoveryOutcome.MARKED_AND_FINALIZED;
        } catch (RuntimeException e) {
            DriveUpload latest = uploadRepository.findById(upload.uploadId()).orElse(upload);
            if (latest.status() == DriveUploadStatus.FAILED) {
                return markedObjectCompleted ? RecoveryOutcome.MARKED_AND_FAILED : RecoveryOutcome.FAILED;
            }
            return markedObjectCompleted ? RecoveryOutcome.MARKED_AND_SKIPPED : RecoveryOutcome.SKIPPED;
        }
    }

    private void expirePreparingUpload(UUID uploadId) {
        transactionOperations.requiresNew(() -> {
            DriveUpload current = uploadApplicationService.loadUpload(uploadId);
            if (current.status() != DriveUploadStatus.PREPARING) {
                return;
            }
            uploadRepository.transitionStatus(current.expirePreparation(clock.instant()), DriveUploadStatus.PREPARING);
        });
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

    private static RecoveryResult add(RecoveryResult result, RecoveryOutcome outcome) {
        return new RecoveryResult(
                result.prepared() + outcome.prepared(),
                result.finalized() + outcome.finalized(),
                result.markedObjectCompleted() + outcome.markedObjectCompleted(),
                result.failed() + outcome.failed(),
                result.skipped() + outcome.skipped()
        );
    }

    private record RecoveryOutcome(int prepared, int finalized, int markedObjectCompleted, int failed, int skipped) {

        private static final RecoveryOutcome PREPARED = new RecoveryOutcome(1, 0, 0, 0, 0);
        private static final RecoveryOutcome FINALIZED = new RecoveryOutcome(0, 1, 0, 0, 0);
        private static final RecoveryOutcome MARKED_AND_FINALIZED = new RecoveryOutcome(0, 1, 1, 0, 0);
        private static final RecoveryOutcome FAILED = new RecoveryOutcome(0, 0, 0, 1, 0);
        private static final RecoveryOutcome MARKED_AND_FAILED = new RecoveryOutcome(0, 0, 1, 1, 0);
        private static final RecoveryOutcome SKIPPED = new RecoveryOutcome(0, 0, 0, 0, 1);
        private static final RecoveryOutcome MARKED_AND_SKIPPED = new RecoveryOutcome(0, 0, 1, 0, 1);
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
}
