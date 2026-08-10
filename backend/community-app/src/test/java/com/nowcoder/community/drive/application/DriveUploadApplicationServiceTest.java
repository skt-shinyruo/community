package com.nowcoder.community.drive.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.drive.application.DriveUploadApplicationService.CompleteUploadCommand;
import com.nowcoder.community.drive.application.command.DriveUploadContent;
import com.nowcoder.community.drive.application.DriveUploadApplicationService.PrepareUploadCommand;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
import com.nowcoder.community.drive.application.DriveUploadApplicationService.RecoveryResult;
import com.nowcoder.community.drive.application.DriveUploadApplicationService.UploadSessionResult;
import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.model.DriveUpload;
import com.nowcoder.community.drive.domain.model.DriveUploadStatus;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.drive.domain.repository.DriveUploadRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriveUploadApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-09T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void prepareUploadShouldRejectNullCommand() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);

        assertThatThrownBy(() -> service.prepareUpload(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void prepareUploadShouldLeavePreparingIntentWhenStorageOutcomeIsUnknown() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        DriveObjectStoragePort storage = mock(DriveObjectStoragePort.class);
        when(storage.prepareUpload(any())).thenThrow(new RuntimeException("response lost"));
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);

        assertThatThrownBy(() -> service.prepareUpload(new PrepareUploadCommand(
                uuid(7), null, "unknown.bin", "application/octet-stream", 8L, "")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("网盘存储服务不可用");

        assertThat(uploads.rows.values()).singleElement()
                .extracting(DriveUpload::status)
                .isEqualTo(DriveUploadStatus.PREPARING);
    }

    @Test
    void recoverStaleUploadsShouldReplayPreparingWithStableRequestId() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        DriveSpace space = DriveSpace.createDefault(uuid(8), userId, NOW);
        spaces.save(space);
        DriveUpload preparing = DriveUpload.preparing(
                uuid(9), space.spaceId(), null, "retry.bin", 8L, "application/octet-stream", "",
                userId, NOW.minusSeconds(10), NOW.plusSeconds(900));
        uploads.save(preparing);

        RecoveryResult result = service.recoverStaleUploads(NOW, 10);

        assertThat(result.prepared()).isOne();
        assertThat(storage.prepared).singleElement()
                .extracting(DriveObjectStoragePort.PrepareObject::requestId)
                .isEqualTo(preparing.uploadId());
        assertThat(uploads.findById(preparing.uploadId()).orElseThrow().status())
                .isEqualTo(DriveUploadStatus.PREPARED);
    }

    @Test
    void completeUploadShouldRejectNullCommand() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);

        assertThatThrownBy(() -> service.completeUpload(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void prepareUploadShouldCreateSpaceWhenMissingAndReturnProviderFreeInstruction() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);

        UploadSessionResult result = service.prepareUpload(new PrepareUploadCommand(
                userId,
                null,
                "report.pdf",
                "application/pdf",
                1_024L,
                ""
        ));

        assertThat(spaces.findByUserId(userId)).isPresent();
        assertThat(storage.prepared).hasSize(1);
        assertThat(result.fileKey()).isEqualTo("drive/" + result.uploadId() + "/report.pdf");
        assertThat(result.upload().url()).isEqualTo("/api/drive/uploads/" + result.uploadId() + "/complete");
        assertThat(result.upload().method()).isEqualTo("POST");
        assertThat(result.upload().fileField()).isEqualTo("file");
        assertThat(result.upload().fields()).containsEntry("fileKey", result.fileKey());
        assertThat(result.constraints().maxBytes()).isEqualTo(10_737_418_240L);
    }

    @Test
    void prepareUploadShouldRecoverFromDuplicateKeyDuringBootstrap() {
        DriveSpaceRepository spaces = mock(DriveSpaceRepository.class);
        DriveEntryRepository entries = mock(DriveEntryRepository.class);
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        DriveObjectStoragePort storage = mock(DriveObjectStoragePort.class);
        UUID userId = uuid(7);
        UUID existingSpaceId = uuid(90);
        DriveSpace existingSpace = DriveSpace.createDefault(existingSpaceId, userId, NOW);

        when(spaces.findByUserId(userId)).thenReturn(Optional.empty());
        when(spaces.findById(existingSpaceId)).thenReturn(Optional.of(existingSpace));
        when(spaces.create(any(DriveSpace.class))).thenReturn(new DriveSpaceRepository.CreateResult(
                DriveSpaceRepository.CreateStatus.ALREADY_EXISTS,
                existingSpace
        ));
        when(entries.findActiveChildByName(any(), any(), any())).thenReturn(Optional.empty());
        when(storage.prepareUpload(any()))
                .thenReturn(new DriveObjectStoragePort.PreparedObject(uuid(101), uuid(102), uuid(103), NOW.plusSeconds(900)));

        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UploadSessionResult session = service.prepareUpload(new PrepareUploadCommand(userId, null, "report.pdf", "application/pdf", 1_024L, ""));

        assertThat(session.uploadId()).isNotBlank();
        DriveUpload persisted = uploads.findById(UUID.fromString(session.uploadId())).orElseThrow();
        assertThat(persisted.spaceId()).isEqualTo(existingSpaceId);
        assertThat(persisted.status()).isEqualTo(DriveUploadStatus.PREPARED);
    }

    @Test
    void completeUploadShouldProxyToOssCreateEntryAndReserveQuotaOnce() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        UploadSessionResult session = service.prepareUpload(new PrepareUploadCommand(userId, null, "report.pdf", "application/pdf", 1_024L, ""));

        DriveEntryResult first = service.completeUpload(new CompleteUploadCommand(
                userId,
                UUID.fromString(session.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L)
        ));
        DriveEntryResult second = service.completeUpload(new CompleteUploadCommand(
                userId,
                UUID.fromString(session.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L)
        ));

        assertThat(first.entryId()).isEqualTo(second.entryId());
        assertThat(first.entryId().version()).isEqualTo(7);
        assertThat(first.name()).isEqualTo("report.pdf");
        assertThat(first.type()).isEqualTo("FILE");
        assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isEqualTo(1_024L);
        assertThat(storage.completed).hasSize(1);
    }

    @Test
    void completeUploadShouldUseTheChecksumPersistedAtPrepare() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        UploadSessionResult session = service.prepareUpload(new PrepareUploadCommand(
                userId,
                null,
                "report.pdf",
                "application/pdf",
                1_024L,
                "sha256:expected"
        ));

        service.completeUpload(new CompleteUploadCommand(
                userId,
                UUID.fromString(session.uploadId()),
                new DriveUploadContent(
                        () -> new ByteArrayInputStream("file".getBytes()),
                        "application/pdf",
                        1_024L
                )
        ));

        assertThat(storage.completed)
                .singleElement()
                .extracting(DriveObjectStoragePort.CompleteObject::checksumSha256)
                .isEqualTo("sha256:expected");
        assertThat(uploads.findById(UUID.fromString(session.uploadId())).orElseThrow().checksumSha256())
                .isEqualTo("sha256:expected");
    }

    @Test
    void completeUploadShouldLeaveObjectCompletedRecoverableWhenEntrySaveFailsAfterOss() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        UploadSessionResult session = service.prepareUpload(new PrepareUploadCommand(userId, null, "report.pdf", "application/pdf", 1_024L, ""));
        UUID uploadId = UUID.fromString(session.uploadId());

        entries.returnNextCreate(DriveEntryRepository.CreateStatus.CONFLICT);

        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(
                userId,
                uploadId,
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L)
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("网盘条目创建失败");

        DriveUpload recoverable = uploads.findById(uploadId).orElseThrow();
        assertThat(recoverable.status()).isEqualTo(DriveUploadStatus.OBJECT_COMPLETED);
        assertThat(recoverable.completedEntryId()).isNotNull();
        assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isZero();
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isEqualTo(1_024L);
        assertThat(storage.completed).hasSize(1);
        assertThat(storage.deletedObjects).isEmpty();

        DriveEntryResult recovered = service.completeUpload(new CompleteUploadCommand(
                userId,
                uploadId,
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L)
        ));

        assertThat(recovered.entryId()).isEqualTo(recoverable.completedEntryId());
        assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isEqualTo(1_024L);
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isZero();
        assertThat(uploads.findById(uploadId).orElseThrow().status()).isEqualTo(DriveUploadStatus.COMPLETED);
        assertThat(storage.completed).hasSize(1);
    }

    @Test
    void recoverStaleUploadsShouldFinalizeObjectCompletedAndResolveCompletingByOssMetadata() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        UploadSessionResult objectCompletedSession = service.prepareUpload(new PrepareUploadCommand(userId, null, "recoverable.txt", "text/plain", 1_024L, ""));
        UUID objectCompletedUploadId = UUID.fromString(objectCompletedSession.uploadId());
        entries.returnNextCreate(DriveEntryRepository.CreateStatus.CONFLICT);
        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(
                userId,
                objectCompletedUploadId,
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "text/plain", 1_024L)
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("网盘条目创建失败");

        UploadSessionResult completingSession = service.prepareUpload(new PrepareUploadCommand(userId, null, "unknown.txt", "text/plain", 512L, ""));
        UUID completingUploadId = UUID.fromString(completingSession.uploadId());
        DriveUpload prepared = uploads.findById(completingUploadId).orElseThrow();
        assertThat(spaces.reserve(prepared.spaceId(), prepared.sizeBytes(), NOW)).isTrue();
        assertThat(uploads.transitionStatus(prepared.startCompleting(uuid(500), NOW), DriveUploadStatus.PREPARED)).isTrue();
        storage.metadataStatuses.put(prepared.objectId(), "PURGED");

        RecoveryResult result = service.recoverStaleUploads(NOW.plusSeconds(1), 10);

        assertThat(result.finalized()).isEqualTo(1);
        assertThat(result.markedObjectCompleted()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        assertThat(uploads.findById(objectCompletedUploadId).orElseThrow().status()).isEqualTo(DriveUploadStatus.COMPLETED);
        assertThat(uploads.findById(completingUploadId).orElseThrow().status()).isEqualTo(DriveUploadStatus.FAILED);
        assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isEqualTo(1_024L);
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isZero();
        assertThat(storage.completed).hasSize(1);
    }

    @Test
    void completeUploadShouldKeepCompletingRecoverableWhenOssFailsButObjectIsActive() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        UploadSessionResult session = service.prepareUpload(new PrepareUploadCommand(userId, null, "late-response.txt", "text/plain", 512L, ""));
        UUID uploadId = UUID.fromString(session.uploadId());
        storage.failAfterObjectCompleted = true;

        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(
                userId,
                uploadId,
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "text/plain", 512L)
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("网盘存储服务不可用");

        DriveUpload recoverable = uploads.findById(uploadId).orElseThrow();
        assertThat(recoverable.status()).isEqualTo(DriveUploadStatus.OBJECT_COMPLETED);
        assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isZero();
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isEqualTo(512L);

        RecoveryResult result = service.recoverStaleUploads(NOW.plusSeconds(1), 10);

        assertThat(result.finalized()).isEqualTo(1);
        assertThat(uploads.findById(uploadId).orElseThrow().status()).isEqualTo(DriveUploadStatus.COMPLETED);
        assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isEqualTo(512L);
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isZero();
        assertThat(storage.completed).hasSize(1);
    }

    @Test
    void recoverStaleUploadsShouldReleaseExpiredUnknownCompletion() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        UploadSessionResult session = service.prepareUpload(new PrepareUploadCommand(
                userId, null, "unknown-outcome.txt", "text/plain", 512L, ""));
        UUID uploadId = UUID.fromString(session.uploadId());
        DriveUpload prepared = uploads.findById(uploadId).orElseThrow();
        assertThat(spaces.reserve(prepared.spaceId(), prepared.sizeBytes(), NOW)).isTrue();
        assertThat(uploads.transitionStatus(
                prepared.startCompleting(uuid(500), NOW), DriveUploadStatus.PREPARED)).isTrue();
        storage.metadataUnavailable = true;

        RecoveryResult beforeDeadline = service(
                spaces, entries, uploads, storage, Clock.fixed(NOW.plusSeconds(3_599), ZoneOffset.UTC))
                .recoverStaleUploads(NOW.plusSeconds(3_600), 10);

        assertThat(beforeDeadline.skipped()).isEqualTo(1);
        assertThat(uploads.findById(uploadId).orElseThrow().status()).isEqualTo(DriveUploadStatus.COMPLETING);
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isEqualTo(512L);

        RecoveryResult atDeadline = service(
                spaces, entries, uploads, storage, Clock.fixed(NOW.plusSeconds(3_600), ZoneOffset.UTC))
                .recoverStaleUploads(NOW.plusSeconds(3_601), 10);

        assertThat(atDeadline.failed()).isEqualTo(1);
        assertThat(uploads.findById(uploadId).orElseThrow().status()).isEqualTo(DriveUploadStatus.FAILED);
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isZero();
        assertThat(storage.deletedObjects).containsExactly(prepared.objectId());
    }

    @Test
    void recoverStaleUploadsShouldKeepOssUploadsInProgressBeforeDeadline() {
        for (String ossStatus : List.of("STAGED", "UPLOADING")) {
            InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
            InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
            InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
            FakeStoragePort storage = new FakeStoragePort();
            UUID userId = uuid(7);
            UploadSessionResult session = service(spaces, entries, uploads, storage)
                    .prepareUpload(new PrepareUploadCommand(
                            userId, null, ossStatus.toLowerCase() + ".txt", "text/plain", 512L, ""));
            UUID uploadId = UUID.fromString(session.uploadId());
            DriveUpload prepared = uploads.findById(uploadId).orElseThrow();
            assertThat(spaces.reserve(prepared.spaceId(), prepared.sizeBytes(), NOW)).isTrue();
            assertThat(uploads.transitionStatus(
                    prepared.startCompleting(uuid(500), NOW), DriveUploadStatus.PREPARED)).isTrue();
            storage.metadataStatuses.put(prepared.objectId(), ossStatus);

            RecoveryResult result = service(
                    spaces, entries, uploads, storage, Clock.fixed(NOW.plusSeconds(3_599), ZoneOffset.UTC))
                    .recoverStaleUploads(NOW.plusSeconds(3_600), 10);

            assertThat(result.skipped()).as(ossStatus).isEqualTo(1);
            assertThat(uploads.findById(uploadId).orElseThrow().status())
                    .as(ossStatus)
                    .isEqualTo(DriveUploadStatus.COMPLETING);
            assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes())
                    .as(ossStatus)
                    .isEqualTo(512L);
            assertThat(storage.cancelledSessions).as(ossStatus).isEmpty();
            assertThat(storage.deletedObjects).as(ossStatus).isEmpty();
        }
    }

    @Test
    void recoverExpiredUnknownCompletionShouldFinalizeWhenOssCompletionWonCancellationRace() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        UUID userId = uuid(7);
        UploadSessionResult session = service(spaces, entries, uploads, storage)
                .prepareUpload(new PrepareUploadCommand(
                        userId, null, "cancel-race.txt", "text/plain", 512L, ""));
        UUID uploadId = UUID.fromString(session.uploadId());
        DriveUpload prepared = uploads.findById(uploadId).orElseThrow();
        assertThat(spaces.reserve(prepared.spaceId(), prepared.sizeBytes(), NOW)).isTrue();
        assertThat(uploads.transitionStatus(
                prepared.startCompleting(uuid(500), NOW), DriveUploadStatus.PREPARED)).isTrue();
        storage.metadataUnavailable = true;
        storage.cancellationCompleted = true;

        RecoveryResult result = service(
                spaces, entries, uploads, storage, Clock.fixed(NOW.plusSeconds(3_600), ZoneOffset.UTC))
                .recoverStaleUploads(NOW.plusSeconds(3_601), 10);

        assertThat(result.markedObjectCompleted()).isEqualTo(1);
        assertThat(result.finalized()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(uploads.findById(uploadId).orElseThrow().status()).isEqualTo(DriveUploadStatus.COMPLETED);
        assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isEqualTo(512L);
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isZero();
        assertThat(storage.cancelledSessions).containsExactly(prepared.ossSessionId());
        assertThat(storage.deletedObjects).isEmpty();
    }

    @Test
    void recoverExpiredUnknownCompletionShouldRetainReservationUntilOssConfirmsCancellation() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        UUID userId = uuid(7);
        UploadSessionResult session = service(spaces, entries, uploads, storage)
                .prepareUpload(new PrepareUploadCommand(
                        userId, null, "cancel-retry.txt", "text/plain", 512L, ""));
        UUID uploadId = UUID.fromString(session.uploadId());
        DriveUpload prepared = uploads.findById(uploadId).orElseThrow();
        assertThat(spaces.reserve(prepared.spaceId(), prepared.sizeBytes(), NOW)).isTrue();
        assertThat(uploads.transitionStatus(
                prepared.startCompleting(uuid(500), NOW), DriveUploadStatus.PREPARED)).isTrue();
        storage.metadataUnavailable = true;
        storage.cancelFailuresRemaining = 1;

        RecoveryResult first = service(
                spaces, entries, uploads, storage, Clock.fixed(NOW.plusSeconds(3_600), ZoneOffset.UTC))
                .recoverStaleUploads(NOW.plusSeconds(3_601), 10);

        assertThat(first.skipped()).isEqualTo(1);
        assertThat(uploads.findById(uploadId).orElseThrow().status()).isEqualTo(DriveUploadStatus.COMPLETING);
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isEqualTo(512L);
        assertThat(storage.deletedObjects).isEmpty();

        RecoveryResult retried = service(
                spaces, entries, uploads, storage, Clock.fixed(NOW.plusSeconds(3_601), ZoneOffset.UTC))
                .recoverStaleUploads(NOW.plusSeconds(3_602), 10);

        assertThat(retried.failed()).isEqualTo(1);
        assertThat(uploads.findById(uploadId).orElseThrow().status()).isEqualTo(DriveUploadStatus.FAILED);
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isZero();
        assertThat(storage.cancelledSessions)
                .containsExactly(prepared.ossSessionId(), prepared.ossSessionId());
        assertThat(storage.deletedObjects).containsExactly(prepared.objectId());
    }

    @Test
    void recoverStaleUploadsShouldRetryPendingCleanupAfterDeletionFailure() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        UUID userId = uuid(7);
        UploadSessionResult session = service(spaces, entries, uploads, storage)
                .prepareUpload(new PrepareUploadCommand(
                        userId, null, "cleanup-retry.txt", "text/plain", 512L, ""));
        UUID uploadId = UUID.fromString(session.uploadId());
        DriveUpload prepared = uploads.findById(uploadId).orElseThrow();
        assertThat(spaces.reserve(prepared.spaceId(), prepared.sizeBytes(), NOW)).isTrue();
        assertThat(uploads.transitionStatus(
                prepared.startCompleting(uuid(500), NOW), DriveUploadStatus.PREPARED)).isTrue();
        storage.metadataStatuses.put(prepared.objectId(), "STAGED");
        storage.deleteFailuresRemaining = 1;

        RecoveryResult first = service(
                spaces, entries, uploads, storage, Clock.fixed(NOW.plusSeconds(3_600), ZoneOffset.UTC))
                .recoverStaleUploads(NOW.plusSeconds(3_601), 10);

        assertThat(first.failed()).isZero();
        assertThat(first.skipped()).isEqualTo(1);
        assertThat(uploads.findById(uploadId).orElseThrow().status())
                .isEqualTo(DriveUploadStatus.CLEANUP_PENDING);
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isZero();
        assertThat(storage.cancelledSessions).containsExactly(prepared.ossSessionId());
        assertThat(storage.deleteAttempts).containsExactly(prepared.objectId());
        assertThat(storage.deletedObjects).isEmpty();

        RecoveryResult retried = service(
                spaces, entries, uploads, storage, Clock.fixed(NOW.plusSeconds(3_601), ZoneOffset.UTC))
                .recoverStaleUploads(NOW.plusSeconds(3_602), 10);

        assertThat(retried.failed()).isEqualTo(1);
        assertThat(uploads.findById(uploadId).orElseThrow().status()).isEqualTo(DriveUploadStatus.FAILED);
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isZero();
        assertThat(storage.cancelledSessions).containsExactly(prepared.ossSessionId());
        assertThat(storage.deleteAttempts).containsExactly(prepared.objectId(), prepared.objectId());
        assertThat(storage.deletedObjects).containsExactly(prepared.objectId());
    }

    @Test
    void recoverStaleUploadsShouldCompleteActiveObjectAfterRecoveryDeadline() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        UploadSessionResult session = service.prepareUpload(new PrepareUploadCommand(
                userId, null, "confirmed.txt", "text/plain", 512L, ""));
        UUID uploadId = UUID.fromString(session.uploadId());
        DriveUpload prepared = uploads.findById(uploadId).orElseThrow();
        assertThat(spaces.reserve(prepared.spaceId(), prepared.sizeBytes(), NOW)).isTrue();
        DriveUpload completing = prepared.startCompleting(uuid(500), NOW);
        assertThat(uploads.transitionStatus(completing, DriveUploadStatus.PREPARED)).isTrue();
        storage.completeUpload(new DriveObjectStoragePort.CompleteObject(
                completing.ossSessionId(),
                completing.objectId(),
                completing.versionId(),
                completing.name(),
                completing.mimeType(),
                completing.sizeBytes(),
                completing.checksumSha256(),
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "text/plain", 512L)
        ));

        RecoveryResult result = service(
                spaces, entries, uploads, storage, Clock.fixed(NOW.plusSeconds(3_600), ZoneOffset.UTC))
                .recoverStaleUploads(NOW.plusSeconds(3_601), 10);

        assertThat(result.markedObjectCompleted()).isEqualTo(1);
        assertThat(result.finalized()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(uploads.findById(uploadId).orElseThrow().status()).isEqualTo(DriveUploadStatus.COMPLETED);
        assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isEqualTo(512L);
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isZero();
        assertThat(storage.deletedObjects).isEmpty();
    }

    @Test
    void recoverStaleUploadsShouldKeepExpiredObjectCompletedRecoverableAfterInfrastructureFailure() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        UploadSessionResult session = service.prepareUpload(new PrepareUploadCommand(
                userId, null, "retry-expired.txt", "text/plain", 512L, ""));
        UUID uploadId = UUID.fromString(session.uploadId());
        entries.returnNextCreate(DriveEntryRepository.CreateStatus.CONFLICT);
        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(
                userId,
                uploadId,
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "text/plain", 512L)
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("网盘条目创建失败");
        entries.returnNextCreate(DriveEntryRepository.CreateStatus.CONFLICT);

        RecoveryResult failedAttempt = service(
                spaces, entries, uploads, storage, Clock.fixed(NOW.plusSeconds(3_600), ZoneOffset.UTC))
                .recoverStaleUploads(NOW.plusSeconds(3_601), 10);

        DriveUpload recoverable = uploads.findById(uploadId).orElseThrow();
        assertThat(failedAttempt.failed()).isZero();
        assertThat(failedAttempt.finalized()).isZero();
        assertThat(failedAttempt.skipped()).isEqualTo(1);
        assertThat(recoverable.status()).isEqualTo(DriveUploadStatus.OBJECT_COMPLETED);
        assertThat(recoverable.expiredAt(NOW.plusSeconds(3_600))).isTrue();
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isEqualTo(512L);
        assertThat(storage.deletedObjects).isEmpty();

        RecoveryResult retried = service(
                spaces, entries, uploads, storage, Clock.fixed(NOW.plusSeconds(3_601), ZoneOffset.UTC))
                .recoverStaleUploads(NOW.plusSeconds(3_602), 10);

        assertThat(retried.finalized()).isEqualTo(1);
        assertThat(uploads.findById(uploadId).orElseThrow().status()).isEqualTo(DriveUploadStatus.COMPLETED);
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isZero();
        assertThat(storage.deletedObjects).isEmpty();
    }

    @Test
    void recoverStaleUploadsShouldRotateFailedCleanupBeyondFixedBatch() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        UUID userId = uuid(7);
        DriveUploadApplicationService initialService = service(spaces, entries, uploads, storage);
        UUID firstUploadId = UUID.fromString(initialService.prepareUpload(new PrepareUploadCommand(
                userId, null, "blocked-cleanup.txt", "text/plain", 10L, "")).uploadId());
        UUID secondUploadId = UUID.fromString(initialService.prepareUpload(new PrepareUploadCommand(
                userId, null, "next-cleanup.txt", "text/plain", 20L, "")).uploadId());
        DriveUpload firstPrepared = uploads.findById(firstUploadId).orElseThrow();
        DriveUpload secondPrepared = uploads.findById(secondUploadId).orElseThrow();
        DriveUpload firstCompleting = firstPrepared.startCompleting(uuid(501), NOW);
        DriveUpload secondCompleting = secondPrepared.startCompleting(uuid(502), NOW);
        assertThat(uploads.transitionStatus(firstCompleting, DriveUploadStatus.PREPARED)).isTrue();
        assertThat(uploads.transitionStatus(secondCompleting, DriveUploadStatus.PREPARED)).isTrue();
        assertThat(uploads.transitionStatus(
                firstCompleting.startCleanup(NOW.plusSeconds(1)), DriveUploadStatus.COMPLETING)).isTrue();
        assertThat(uploads.transitionStatus(
                secondCompleting.startCleanup(NOW.plusSeconds(2)), DriveUploadStatus.COMPLETING)).isTrue();
        storage.alwaysFailDeleteObjectId = firstPrepared.objectId();
        DriveUploadApplicationService recoveryService = service(
                spaces, entries, uploads, storage, Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC));

        RecoveryResult firstBatch = recoveryService.recoverStaleUploads(NOW.plusSeconds(10), 1);
        RecoveryResult secondBatch = recoveryService.recoverStaleUploads(NOW.plusSeconds(10), 1);

        assertThat(firstBatch.skipped()).isEqualTo(1);
        assertThat(secondBatch.failed()).isEqualTo(1);
        assertThat(uploads.findById(firstUploadId).orElseThrow().status())
                .isEqualTo(DriveUploadStatus.CLEANUP_PENDING);
        assertThat(uploads.findById(secondUploadId).orElseThrow().status()).isEqualTo(DriveUploadStatus.FAILED);
        assertThat(storage.deleteAttempts)
                .containsExactly(firstPrepared.objectId(), secondPrepared.objectId());
    }

    @Test
    void recoverStaleUploadsShouldFinalizeObjectCompletedAfterRecoveryDeadlineWhenPossible() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        UploadSessionResult session = service.prepareUpload(new PrepareUploadCommand(
                userId, null, "retry-success.txt", "text/plain", 512L, ""));
        UUID uploadId = UUID.fromString(session.uploadId());
        entries.returnNextCreate(DriveEntryRepository.CreateStatus.CONFLICT);
        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(
                userId,
                uploadId,
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "text/plain", 512L)
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("网盘条目创建失败");

        RecoveryResult result = service(
                spaces, entries, uploads, storage, Clock.fixed(NOW.plusSeconds(3_600), ZoneOffset.UTC))
                .recoverStaleUploads(NOW.plusSeconds(3_601), 10);

        assertThat(result.finalized()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(uploads.findById(uploadId).orElseThrow().status()).isEqualTo(DriveUploadStatus.COMPLETED);
        assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isEqualTo(512L);
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isZero();
        assertThat(storage.deletedObjects).isEmpty();
    }

    @Test
    void completeUploadShouldFailAndReleaseReservationWhenDuplicateAppearsAfterOssCompletion() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        UploadSessionResult session = service.prepareUpload(new PrepareUploadCommand(userId, null, "report.pdf", "application/pdf", 1_024L, ""));
        UUID uploadId = UUID.fromString(session.uploadId());
        DriveSpace space = spaces.findByUserId(userId).orElseThrow();
        storage.afterComplete = () -> entries.save(DriveEntry.file(uuid(82), space.spaceId(), null, "report.pdf", uuid(83), uuid(84), 10L, "application/pdf", NOW.plusSeconds(1)));

        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(
                userId,
                uploadId,
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L)
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("同名文件或文件夹已存在");

        DriveUpload failed = uploads.findById(uploadId).orElseThrow();
        assertThat(failed.status()).isEqualTo(DriveUploadStatus.FAILED);
        assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isZero();
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isZero();
        assertThat(storage.deletedObjects).containsExactly(failed.objectId());
    }

    @Test
    void recoverStaleUploadsShouldFailAndReleaseReservationWhenParentIsNoLongerActive() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        DriveSpace space = DriveSpace.createDefault(uuid(90), userId, NOW);
        DriveEntry parent = DriveEntry.folder(uuid(91), space.spaceId(), null, "work", NOW);
        spaces.save(space);
        entries.save(parent);
        UploadSessionResult session = service.prepareUpload(new PrepareUploadCommand(userId, parent.entryId(), "report.pdf", "application/pdf", 1_024L, ""));
        UUID uploadId = UUID.fromString(session.uploadId());

        service.completeUpload(new CompleteUploadCommand(
                userId,
                uploadId,
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L)
        ));
        entries.rows.remove(uploads.findById(uploadId).orElseThrow().completedEntryId());
        uploads.forceStatus(uploadId, DriveUploadStatus.OBJECT_COMPLETED, NOW.plusSeconds(2));
        spaces.forceReserved(space.spaceId(), 1_024L, NOW.plusSeconds(2));
        entries.save(parent.trash(NOW.plusSeconds(3), NOW.plusSeconds(86_400)));

        RecoveryResult result = service.recoverStaleUploads(NOW.plusSeconds(10), 10);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.finalized()).isZero();
        assertThat(uploads.findById(uploadId).orElseThrow().status()).isEqualTo(DriveUploadStatus.FAILED);
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isZero();
        assertThat(storage.deletedObjects).contains(uploads.findById(uploadId).orElseThrow().objectId());
    }

    @Test
    void completeUploadShouldFailSecondConcurrentReservationWithoutOverwritingQuota() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        DriveSpace space = DriveSpace.createDefault(uuid(50), userId, NOW);
        spaces.save(space);
        spaces.captureSnapshot(space.spaceId());
        long uploadSize = 6_000_000_000L;
        UploadSessionResult firstSession = service.prepareUpload(new PrepareUploadCommand(userId, null, "first.bin", "application/octet-stream", uploadSize, ""));
        UploadSessionResult secondSession = service.prepareUpload(new PrepareUploadCommand(userId, null, "second.bin", "application/octet-stream", uploadSize, ""));

        DriveEntryResult first = service.completeUpload(new CompleteUploadCommand(
                userId,
                UUID.fromString(firstSession.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("first".getBytes()), "application/octet-stream", uploadSize)
        ));
        assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isEqualTo(uploadSize);

        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(
                userId,
                UUID.fromString(secondSession.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("second".getBytes()), "application/octet-stream", uploadSize)
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("网盘容量不足");
        assertThat(first.name()).isEqualTo("first.bin");
        assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isEqualTo(uploadSize);
        assertThat(storage.completed).hasSize(1);
    }

    @Test
    void prepareUploadShouldRejectQuotaExceededBeforeCallingOss() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);

        assertThatThrownBy(() -> service.prepareUpload(new PrepareUploadCommand(
                uuid(7),
                null,
                "too-large.bin",
                "application/octet-stream",
                10_737_418_241L,
                ""
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("网盘容量不足");
        assertThat(storage.prepared).isEmpty();
    }

    @Test
    void prepareUploadShouldRejectTrashedParentBeforeCallingOss() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        DriveSpace space = DriveSpace.createDefault(uuid(80), userId, NOW);
        DriveEntry trashedParent = DriveEntry.folder(uuid(81), space.spaceId(), null, "old", NOW)
                .trash(NOW.plusSeconds(1), NOW.plusSeconds(86_400));
        spaces.save(space);
        entries.save(trashedParent);

        assertThatThrownBy(() -> service.prepareUpload(new PrepareUploadCommand(
                userId,
                trashedParent.entryId(),
                "report.pdf",
                "application/pdf",
                1_024L,
                ""
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("目标文件夹不存在");
        assertThat(storage.prepared).isEmpty();
    }

    @Test
    void completeUploadShouldRejectContentLengthMismatchBeforeCallingOss() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        UploadSessionResult session = service.prepareUpload(new PrepareUploadCommand(userId, null, "report.pdf", "application/pdf", 1_024L, ""));

        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(
                userId,
                UUID.fromString(session.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 512L)
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("上传文件大小不匹配");
        assertThat(storage.completed).isEmpty();
    }

    @Test
    void completeUploadShouldPersistExpiredStatusEvenWhenItThrows() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        UploadSessionResult session = service.prepareUpload(new PrepareUploadCommand(userId, null, "report.pdf", "application/pdf", 1_024L, ""));
        uploads.forceExpire(UUID.fromString(session.uploadId()), NOW.plusSeconds(901));

        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(
                userId,
                UUID.fromString(session.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L)
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("上传会话不可用");
        assertThat(uploads.findById(UUID.fromString(session.uploadId())).orElseThrow().status().name()).isEqualTo("EXPIRED");
    }

    @Test
    void completeUploadShouldRejectDuplicateCreatedAfterPrepareBeforeCallingOss() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        UploadSessionResult session = service.prepareUpload(new PrepareUploadCommand(userId, null, "report.pdf", "application/pdf", 1_024L, ""));
        DriveSpace space = spaces.findByUserId(userId).orElseThrow();
        entries.save(DriveEntry.file(uuid(82), space.spaceId(), null, "report.pdf", uuid(83), uuid(84), 10L, "application/pdf", NOW.plusSeconds(1)));

        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(
                userId,
                UUID.fromString(session.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L)
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("同名文件或文件夹已存在");
        assertThat(storage.completed).isEmpty();
    }

    @Test
    void completeUploadShouldRejectParentTrashedAfterPrepareBeforeCallingOss() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        DriveSpace space = DriveSpace.createDefault(uuid(90), userId, NOW);
        DriveEntry parent = DriveEntry.folder(uuid(91), space.spaceId(), null, "work", NOW);
        spaces.save(space);
        entries.save(parent);
        UploadSessionResult session = service.prepareUpload(new PrepareUploadCommand(userId, parent.entryId(), "report.pdf", "application/pdf", 1_024L, ""));
        entries.save(parent.trash(NOW.plusSeconds(1), NOW.plusSeconds(86_400)));

        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(
                userId,
                UUID.fromString(session.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L)
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("目标文件夹不存在");
        assertThat(storage.completed).isEmpty();
    }

    private static DriveUploadApplicationService service(
            DriveSpaceRepository spaces,
            DriveEntryRepository entries,
            DriveUploadRepository uploads,
            DriveObjectStoragePort storage
    ) {
        return service(spaces, entries, uploads, storage, CLOCK);
    }

    private static DriveUploadApplicationService service(
            DriveSpaceRepository spaces,
            DriveEntryRepository entries,
            DriveUploadRepository uploads,
            DriveObjectStoragePort storage,
            Clock clock
    ) {
        return new DriveUploadApplicationService(
                spaces,
                entries,
                uploads,
                storage,
                clock,
                DirectDriveTransactionOperations.INSTANCE,
                new UuidV7Generator(clock)
        );
    }

    private static final class InMemoryDriveSpaceRepository implements DriveSpaceRepository {
        private final Map<UUID, DriveSpace> stored = new LinkedHashMap<>();
        private final Map<UUID, DriveSpace> snapshots = new LinkedHashMap<>();
        private int lockCount;

        @Override
        public Optional<DriveSpace> findByUserId(UUID userId) {
            return stored.values().stream()
                    .filter(space -> space.userId().equals(userId))
                    .findFirst();
        }

        @Override
        public Optional<DriveSpace> findById(UUID spaceId) {
            return Optional.ofNullable(snapshots.getOrDefault(spaceId, stored.get(spaceId)));
        }

        @Override
        public DriveSpace lockById(UUID spaceId) {
            DriveSpace space = stored.get(spaceId);
            if (space != null) {
                lockCount++;
            }
            return space;
        }

        @Override
        public void save(DriveSpace space) {
            stored.put(space.spaceId(), space);
        }

        @Override
        public boolean reserve(UUID spaceId, long bytes, Instant now) {
            DriveSpace space = stored.get(spaceId);
            if (space == null || bytes < 0 || space.usedBytes() + space.reservedBytes() + bytes > space.quotaBytes()) {
                return false;
            }
            stored.put(spaceId, space.reserve(bytes, now));
            return true;
        }

        @Override
        public boolean commitReserved(UUID spaceId, long bytes, Instant now) {
            DriveSpace space = stored.get(spaceId);
            if (space == null || bytes < 0 || bytes > space.reservedBytes() || space.usedBytes() + bytes > space.quotaBytes()) {
                return false;
            }
            stored.put(spaceId, space.commitReserved(bytes, now));
            return true;
        }

        @Override
        public boolean releaseReserved(UUID spaceId, long bytes, Instant now) {
            DriveSpace space = stored.get(spaceId);
            if (space == null || bytes < 0) {
                return false;
            }
            stored.put(spaceId, space.releaseReserved(bytes, now));
            return true;
        }

        @Override
        public CreateResult create(DriveSpace space) {
            stored.put(space.spaceId(), space);
            return new CreateResult(CreateStatus.CREATED, space);
        }

        void captureSnapshot(UUID spaceId) {
            snapshots.put(spaceId, stored.get(spaceId));
        }

        void forceReserved(UUID spaceId, long reservedBytes, Instant updatedAt) {
            DriveSpace current = stored.get(spaceId);
            stored.put(spaceId, new DriveSpace(
                    current.spaceId(),
                    current.userId(),
                    current.quotaBytes(),
                    current.usedBytes(),
                    reservedBytes,
                    current.createdAt(),
                    updatedAt
            ));
        }
    }

    private static final class InMemoryDriveEntryRepository implements DriveEntryRepository {
        private final Map<UUID, DriveEntry> rows = new LinkedHashMap<>();

        @Override
        public Optional<DriveEntry> findById(UUID spaceId, UUID entryId) {
            DriveEntry entry = rows.get(entryId);
            return entry != null && entry.spaceId().equals(spaceId) ? Optional.of(entry) : Optional.empty();
        }

        @Override
        public List<DriveEntry> findByIds(UUID spaceId, List<UUID> entryIds) {
            return List.of();
        }

        @Override
        public Optional<DriveEntry> findActiveChildByName(UUID spaceId, UUID parentId, String name) {
            return rows.values().stream()
                    .filter(entry -> entry.spaceId().equals(spaceId))
                    .filter(entry -> parentId == null ? entry.parentId() == null : parentId.equals(entry.parentId()))
                    .filter(entry -> entry.name().equals(name))
                    .filter(entry -> entry.status() == DriveEntryStatus.ACTIVE)
                    .findFirst();
        }

        @Override
        public List<DriveEntry> listActiveChildren(UUID spaceId, UUID parentId) {
            return List.of();
        }

        @Override
        public List<DriveEntry> listTrash(UUID spaceId) {
            return List.of();
        }

        @Override
        public List<DriveEntry> searchActive(UUID spaceId, String keyword, int limit) {
            return List.of();
        }

        @Override
        public List<UUID> listDescendantIds(UUID spaceId, UUID folderId) {
            return List.of();
        }

        @Override
        public boolean markDeletedIfTrashed(DriveEntry deletedEntry) {
            DriveEntry current = rows.get(deletedEntry.entryId());
            if (current == null
                    || !current.spaceId().equals(deletedEntry.spaceId())
                    || current.status() != DriveEntryStatus.TRASHED) {
                return false;
            }
            rows.put(deletedEntry.entryId(), deletedEntry);
            return true;
        }

        @Override
        public void save(DriveEntry entry) {
            rows.put(entry.entryId(), entry);
        }

        private CreateStatus nextCreateStatus;

        @Override
        public CreateResult create(DriveEntry entry) {
            if (nextCreateStatus != null) {
                CreateStatus status = nextCreateStatus;
                nextCreateStatus = null;
                return new CreateResult(status, null);
            }
            rows.put(entry.entryId(), entry);
            return new CreateResult(CreateStatus.CREATED, entry);
        }

        void returnNextCreate(CreateStatus status) {
            nextCreateStatus = status;
        }
    }

    private static final class InMemoryDriveUploadRepository implements DriveUploadRepository {
        private final Map<UUID, DriveUpload> rows = new LinkedHashMap<>();

        @Override
        public Optional<DriveUpload> findById(UUID uploadId) {
            return Optional.ofNullable(rows.get(uploadId));
        }

        @Override
        public boolean transitionStatus(DriveUpload upload, DriveUploadStatus expectedStatus) {
            DriveUpload current = rows.get(upload.uploadId());
            if (current == null || current.status() != expectedStatus) {
                return false;
            }
            rows.put(upload.uploadId(), upload);
            return true;
        }

        @Override
        public boolean recordRecoveryAttempt(UUID uploadId, DriveUploadStatus expectedStatus, Instant attemptedAt) {
            DriveUpload current = rows.get(uploadId);
            if (current == null || current.status() != expectedStatus) {
                return false;
            }
            rows.put(uploadId, withUpdatedAt(current, attemptedAt));
            return true;
        }

        @Override
        public List<DriveUpload> listRecoverableBefore(Instant updatedBefore, int limit) {
            if (updatedBefore == null || limit <= 0) {
                return List.of();
            }
            return rows.values().stream()
                    .filter(upload -> upload.status() == DriveUploadStatus.PREPARING
                            || upload.status() == DriveUploadStatus.COMPLETING
                            || upload.status() == DriveUploadStatus.OBJECT_COMPLETED
                            || upload.status() == DriveUploadStatus.CLEANUP_PENDING)
                    .filter(upload -> upload.updatedAt().isBefore(updatedBefore))
                    .sorted(Comparator.comparing(DriveUpload::updatedAt).thenComparing(DriveUpload::uploadId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void save(DriveUpload upload) {
            rows.put(upload.uploadId(), upload);
        }

        void forceExpire(UUID uploadId, Instant now) {
            DriveUpload upload = rows.get(uploadId);
            rows.put(uploadId, upload.complete(UUID.randomUUID(), now));
        }

        void forceStatus(UUID uploadId, DriveUploadStatus status, Instant updatedAt) {
            DriveUpload upload = rows.get(uploadId);
            rows.put(uploadId, new DriveUpload(
                    upload.uploadId(),
                    upload.spaceId(),
                    upload.parentId(),
                    upload.name(),
                    upload.sizeBytes(),
                    upload.mimeType(),
                    upload.checksumSha256(),
                    upload.objectId(),
                    upload.versionId(),
                    upload.ossSessionId(),
                    upload.createdBy(),
                    status,
                    upload.completedEntryId(),
                    upload.createdAt(),
                    updatedAt,
                    upload.expiresAt(),
                    upload.completedAt()
            ));
        }

        private static DriveUpload withUpdatedAt(DriveUpload upload, Instant updatedAt) {
            return new DriveUpload(
                    upload.uploadId(), upload.spaceId(), upload.parentId(), upload.name(), upload.sizeBytes(),
                    upload.mimeType(), upload.checksumSha256(), upload.objectId(), upload.versionId(),
                    upload.ossSessionId(), upload.createdBy(), upload.status(), upload.completedEntryId(),
                    upload.createdAt(), updatedAt, upload.expiresAt(), upload.completedAt()
            );
        }
    }

    private static final class FakeStoragePort implements DriveObjectStoragePort {
        private final List<PrepareObject> prepared = new ArrayList<>();
        private final List<CompleteObject> completed = new ArrayList<>();
        private final Map<UUID, String> metadataStatuses = new LinkedHashMap<>();
        private final List<UUID> cancelledSessions = new ArrayList<>();
        private final List<UUID> deleteAttempts = new ArrayList<>();
        private final List<UUID> deletedObjects = new ArrayList<>();
        private boolean failAfterObjectCompleted;
        private boolean metadataUnavailable;
        private boolean cancellationCompleted;
        private int cancelFailuresRemaining;
        private int deleteFailuresRemaining;
        private UUID alwaysFailDeleteObjectId;
        private Runnable afterComplete;

        @Override
        public PreparedObject prepareUpload(PrepareObject command) {
            prepared.add(command);
            int suffix = 100 + prepared.size();
            return new PreparedObject(uuid(suffix), uuid(suffix + 100), uuid(suffix + 200), NOW.plusSeconds(900));
        }

        @Override
        public StoredObject completeUpload(CompleteObject command) {
            completed.add(command);
            if (afterComplete != null) {
                Runnable callback = afterComplete;
                afterComplete = null;
                callback.run();
            }
            if (failAfterObjectCompleted) {
                failAfterObjectCompleted = false;
                throw new RuntimeException("response lost");
            }
            return new StoredObject(command.objectId(), command.versionId(), "");
        }

        @Override
        public UploadCancellation cancelUpload(UUID sessionId, UUID objectId, UUID versionId) {
            cancelledSessions.add(sessionId);
            if (cancelFailuresRemaining > 0) {
                cancelFailuresRemaining--;
                throw new RuntimeException("cancel unavailable");
            }
            return new UploadCancellation(cancellationCompleted, !cancellationCompleted);
        }

        @Override
        public ObjectMetadata getMetadata(UUID objectId) {
            if (metadataUnavailable) {
                throw new RuntimeException("metadata unavailable");
            }
            String metadataStatus = metadataStatuses.get(objectId);
            if (metadataStatus != null) {
                return new ObjectMetadata(
                        objectId,
                        null,
                        metadataStatus,
                        "",
                        "application/octet-stream",
                        0L,
                        "",
                        ""
                );
            }
            return completed.stream()
                    .filter(command -> command.objectId().equals(objectId))
                    .findFirst()
                    .map(command -> new ObjectMetadata(
                            command.objectId(),
                            command.versionId(),
                            "ACTIVE",
                            command.fileName(),
                            command.contentType(),
                            command.contentLength(),
                            command.checksumSha256(),
                            ""
                    ))
                    .orElse(null);
        }

        @Override
        public SignedDownloadUrl createDownloadUrl(UUID objectId, long ttlSeconds) {
            return new SignedDownloadUrl("https://cdn.example.test/" + objectId, NOW.plusSeconds(ttlSeconds));
        }

        @Override
        public void deleteObject(UUID objectId, String actorId) {
            deleteAttempts.add(objectId);
            if (objectId.equals(alwaysFailDeleteObjectId)) {
                throw new RuntimeException("delete unavailable");
            }
            if (deleteFailuresRemaining > 0) {
                deleteFailuresRemaining--;
                throw new RuntimeException("delete unavailable");
            }
            deletedObjects.add(objectId);
        }
    }
}
