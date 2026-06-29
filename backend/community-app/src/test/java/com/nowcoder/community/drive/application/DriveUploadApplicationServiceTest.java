package com.nowcoder.community.drive.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.drive.application.command.CompleteDriveUploadCommand;
import com.nowcoder.community.drive.application.command.DriveUploadContent;
import com.nowcoder.community.drive.application.command.PrepareDriveUploadCommand;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
import com.nowcoder.community.drive.application.result.DriveUploadRecoveryResult;
import com.nowcoder.community.drive.application.result.DriveUploadSessionResult;
import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.model.DriveUpload;
import com.nowcoder.community.drive.domain.model.DriveUploadStatus;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.drive.domain.repository.DriveUploadRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriveUploadApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-09T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void prepareUploadShouldCreateSpaceWhenMissingAndReturnProviderFreeInstruction() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);

        DriveUploadSessionResult result = service.prepareUpload(new PrepareDriveUploadCommand(
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
        DriveUploadRepository uploads = mock(DriveUploadRepository.class);
        DriveObjectStoragePort storage = mock(DriveObjectStoragePort.class);
        UUID userId = uuid(7);
        UUID existingSpaceId = uuid(90);
        DriveSpace existingSpace = DriveSpace.createDefault(existingSpaceId, userId, NOW);

        when(spaces.findByUserId(userId))
                .thenReturn(Optional.empty(), Optional.of(existingSpace));
        doThrow(new DuplicateKeyException("duplicate drive_space user")).when(spaces).save(any(DriveSpace.class));
        when(entries.findActiveChildByName(any(), any(), any())).thenReturn(Optional.empty());
        when(storage.prepareUpload(any()))
                .thenReturn(new DriveObjectStoragePort.PreparedObject(uuid(101), uuid(102), uuid(103), NOW.plusSeconds(900)));

        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        DriveUploadSessionResult session = service.prepareUpload(new PrepareDriveUploadCommand(userId, null, "report.pdf", "application/pdf", 1_024L, ""));

        assertThat(session.uploadId()).isNotBlank();
        ArgumentCaptor<DriveUpload> uploadCaptor = ArgumentCaptor.forClass(DriveUpload.class);
        verify(uploads).save(uploadCaptor.capture());
        assertThat(uploadCaptor.getValue().spaceId()).isEqualTo(existingSpaceId);
    }

    @Test
    void completeUploadShouldProxyToOssCreateEntryAndReserveQuotaOnce() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        DriveUploadSessionResult session = service.prepareUpload(new PrepareDriveUploadCommand(userId, null, "report.pdf", "application/pdf", 1_024L, ""));

        DriveEntryResult first = service.completeUpload(new CompleteDriveUploadCommand(
                userId,
                UUID.fromString(session.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L, "")
        ));
        DriveEntryResult second = service.completeUpload(new CompleteDriveUploadCommand(
                userId,
                UUID.fromString(session.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L, "")
        ));

        assertThat(first.entryId()).isEqualTo(second.entryId());
        assertThat(first.name()).isEqualTo("report.pdf");
        assertThat(first.type()).isEqualTo("FILE");
        assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isEqualTo(1_024L);
        assertThat(storage.completed).hasSize(1);
    }

    @Test
    void completeUploadShouldLeaveObjectCompletedRecoverableWhenEntrySaveFailsAfterOss() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        DriveUploadSessionResult session = service.prepareUpload(new PrepareDriveUploadCommand(userId, null, "report.pdf", "application/pdf", 1_024L, ""));
        UUID uploadId = UUID.fromString(session.uploadId());

        entries.failNextSave(new DuplicateKeyException("entry insert failed"));

        assertThatThrownBy(() -> service.completeUpload(new CompleteDriveUploadCommand(
                userId,
                uploadId,
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L, "")
        ))).isInstanceOf(DuplicateKeyException.class);

        DriveUpload recoverable = uploads.findById(uploadId).orElseThrow();
        assertThat(recoverable.status()).isEqualTo(DriveUploadStatus.OBJECT_COMPLETED);
        assertThat(recoverable.completedEntryId()).isNotNull();
        assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isZero();
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isEqualTo(1_024L);
        assertThat(storage.completed).hasSize(1);
        assertThat(storage.deletedObjects).isEmpty();

        DriveEntryResult recovered = service.completeUpload(new CompleteDriveUploadCommand(
                userId,
                uploadId,
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L, "")
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
        DriveUploadSessionResult objectCompletedSession = service.prepareUpload(new PrepareDriveUploadCommand(userId, null, "recoverable.txt", "text/plain", 1_024L, ""));
        UUID objectCompletedUploadId = UUID.fromString(objectCompletedSession.uploadId());
        entries.failNextSave(new DuplicateKeyException("entry insert failed"));
        assertThatThrownBy(() -> service.completeUpload(new CompleteDriveUploadCommand(
                userId,
                objectCompletedUploadId,
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "text/plain", 1_024L, "")
        ))).isInstanceOf(DuplicateKeyException.class);

        DriveUploadSessionResult completingSession = service.prepareUpload(new PrepareDriveUploadCommand(userId, null, "unknown.txt", "text/plain", 512L, ""));
        UUID completingUploadId = UUID.fromString(completingSession.uploadId());
        DriveUpload prepared = uploads.findById(completingUploadId).orElseThrow();
        assertThat(spaces.reserve(prepared.spaceId(), prepared.sizeBytes(), NOW)).isTrue();
        assertThat(uploads.transitionStatus(prepared.startCompleting(uuid(500), NOW), DriveUploadStatus.PREPARED)).isTrue();

        DriveUploadRecoveryResult result = service.recoverStaleUploads(NOW.plusSeconds(1), 10);

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
        DriveUploadSessionResult session = service.prepareUpload(new PrepareDriveUploadCommand(userId, null, "late-response.txt", "text/plain", 512L, ""));
        UUID uploadId = UUID.fromString(session.uploadId());
        storage.failAfterObjectCompleted = true;

        assertThatThrownBy(() -> service.completeUpload(new CompleteDriveUploadCommand(
                userId,
                uploadId,
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "text/plain", 512L, "")
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("网盘存储服务不可用");

        DriveUpload recoverable = uploads.findById(uploadId).orElseThrow();
        assertThat(recoverable.status()).isEqualTo(DriveUploadStatus.OBJECT_COMPLETED);
        assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isZero();
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isEqualTo(512L);

        DriveUploadRecoveryResult result = service.recoverStaleUploads(NOW.plusSeconds(1), 10);

        assertThat(result.finalized()).isEqualTo(1);
        assertThat(uploads.findById(uploadId).orElseThrow().status()).isEqualTo(DriveUploadStatus.COMPLETED);
        assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isEqualTo(512L);
        assertThat(spaces.findByUserId(userId).orElseThrow().reservedBytes()).isZero();
        assertThat(storage.completed).hasSize(1);
    }

    @Test
    void completeUploadShouldFailAndReleaseReservationWhenDuplicateAppearsAfterOssCompletion() {
        InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
        InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
        InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
        FakeStoragePort storage = new FakeStoragePort();
        DriveUploadApplicationService service = service(spaces, entries, uploads, storage);
        UUID userId = uuid(7);
        DriveUploadSessionResult session = service.prepareUpload(new PrepareDriveUploadCommand(userId, null, "report.pdf", "application/pdf", 1_024L, ""));
        UUID uploadId = UUID.fromString(session.uploadId());
        DriveSpace space = spaces.findByUserId(userId).orElseThrow();
        storage.afterComplete = () -> entries.save(DriveEntry.file(uuid(82), space.spaceId(), null, "report.pdf", uuid(83), uuid(84), 10L, "application/pdf", NOW.plusSeconds(1)));

        assertThatThrownBy(() -> service.completeUpload(new CompleteDriveUploadCommand(
                userId,
                uploadId,
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L, "")
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
        DriveUploadSessionResult session = service.prepareUpload(new PrepareDriveUploadCommand(userId, parent.entryId(), "report.pdf", "application/pdf", 1_024L, ""));
        UUID uploadId = UUID.fromString(session.uploadId());

        service.completeUpload(new CompleteDriveUploadCommand(
                userId,
                uploadId,
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L, "")
        ));
        entries.rows.remove(uploads.findById(uploadId).orElseThrow().completedEntryId());
        uploads.forceStatus(uploadId, DriveUploadStatus.OBJECT_COMPLETED, NOW.plusSeconds(2));
        spaces.forceReserved(space.spaceId(), 1_024L, NOW.plusSeconds(2));
        entries.save(parent.trash(NOW.plusSeconds(3), NOW.plusSeconds(86_400)));

        DriveUploadRecoveryResult result = service.recoverStaleUploads(NOW.plusSeconds(10), 10);

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
        DriveUploadSessionResult firstSession = service.prepareUpload(new PrepareDriveUploadCommand(userId, null, "first.bin", "application/octet-stream", uploadSize, ""));
        DriveUploadSessionResult secondSession = service.prepareUpload(new PrepareDriveUploadCommand(userId, null, "second.bin", "application/octet-stream", uploadSize, ""));

        DriveEntryResult first = service.completeUpload(new CompleteDriveUploadCommand(
                userId,
                UUID.fromString(firstSession.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("first".getBytes()), "application/octet-stream", uploadSize, "")
        ));
        assertThat(spaces.findByUserId(userId).orElseThrow().usedBytes()).isEqualTo(uploadSize);

        assertThatThrownBy(() -> service.completeUpload(new CompleteDriveUploadCommand(
                userId,
                UUID.fromString(secondSession.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("second".getBytes()), "application/octet-stream", uploadSize, "")
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

        assertThatThrownBy(() -> service.prepareUpload(new PrepareDriveUploadCommand(
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

        assertThatThrownBy(() -> service.prepareUpload(new PrepareDriveUploadCommand(
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
        DriveUploadSessionResult session = service.prepareUpload(new PrepareDriveUploadCommand(userId, null, "report.pdf", "application/pdf", 1_024L, ""));

        assertThatThrownBy(() -> service.completeUpload(new CompleteDriveUploadCommand(
                userId,
                UUID.fromString(session.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 512L, "")
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
        DriveUploadSessionResult session = service.prepareUpload(new PrepareDriveUploadCommand(userId, null, "report.pdf", "application/pdf", 1_024L, ""));
        uploads.forceExpire(UUID.fromString(session.uploadId()), NOW.plusSeconds(901));

        assertThatThrownBy(() -> service.completeUpload(new CompleteDriveUploadCommand(
                userId,
                UUID.fromString(session.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L, "")
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
        DriveUploadSessionResult session = service.prepareUpload(new PrepareDriveUploadCommand(userId, null, "report.pdf", "application/pdf", 1_024L, ""));
        DriveSpace space = spaces.findByUserId(userId).orElseThrow();
        entries.save(DriveEntry.file(uuid(82), space.spaceId(), null, "report.pdf", uuid(83), uuid(84), 10L, "application/pdf", NOW.plusSeconds(1)));

        assertThatThrownBy(() -> service.completeUpload(new CompleteDriveUploadCommand(
                userId,
                UUID.fromString(session.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L, "")
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
        DriveUploadSessionResult session = service.prepareUpload(new PrepareDriveUploadCommand(userId, parent.entryId(), "report.pdf", "application/pdf", 1_024L, ""));
        entries.save(parent.trash(NOW.plusSeconds(1), NOW.plusSeconds(86_400)));

        assertThatThrownBy(() -> service.completeUpload(new CompleteDriveUploadCommand(
                userId,
                UUID.fromString(session.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes()), "application/pdf", 1_024L, "")
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
        return new DriveUploadApplicationService(spaces, entries, uploads, storage, CLOCK);
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
        public void save(DriveEntry entry) {
            if (nextSaveFailure != null) {
                RuntimeException failure = nextSaveFailure;
                nextSaveFailure = null;
                throw failure;
            }
            rows.put(entry.entryId(), entry);
        }

        private RuntimeException nextSaveFailure;

        void failNextSave(RuntimeException failure) {
            nextSaveFailure = failure;
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
        public List<DriveUpload> listRecoverableBefore(Instant updatedBefore, int limit) {
            if (updatedBefore == null || limit <= 0) {
                return List.of();
            }
            return rows.values().stream()
                    .filter(upload -> upload.status() == DriveUploadStatus.COMPLETING
                            || upload.status() == DriveUploadStatus.OBJECT_COMPLETED)
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
    }

    private static final class FakeStoragePort implements DriveObjectStoragePort {
        private final List<PrepareObject> prepared = new ArrayList<>();
        private final List<CompleteObject> completed = new ArrayList<>();
        private final List<UUID> deletedObjects = new ArrayList<>();
        private boolean failAfterObjectCompleted;
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
        public ObjectMetadata getMetadata(UUID objectId) {
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
            deletedObjects.add(objectId);
        }
    }
}
