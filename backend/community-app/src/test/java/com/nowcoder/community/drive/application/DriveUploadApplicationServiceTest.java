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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
        public void save(DriveSpace space) {
            stored.put(space.spaceId(), space);
        }

        @Override
        public boolean reserve(UUID spaceId, long bytes, Instant now) {
            DriveSpace space = stored.get(spaceId);
            if (space == null || bytes < 0 || space.usedBytes() + bytes > space.quotaBytes()) {
                return false;
            }
            stored.put(spaceId, space.reserve(bytes, now));
            return true;
        }

        void captureSnapshot(UUID spaceId) {
            snapshots.put(spaceId, stored.get(spaceId));
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
            rows.put(entry.entryId(), entry);
        }
    }

    private static final class InMemoryDriveUploadRepository implements DriveUploadRepository {
        private final Map<UUID, DriveUpload> rows = new LinkedHashMap<>();

        @Override
        public Optional<DriveUpload> findById(UUID uploadId) {
            return Optional.ofNullable(rows.get(uploadId));
        }

        @Override
        public void save(DriveUpload upload) {
            rows.put(upload.uploadId(), upload);
        }

        void forceExpire(UUID uploadId, Instant now) {
            DriveUpload upload = rows.get(uploadId);
            rows.put(uploadId, upload.complete(UUID.randomUUID(), now));
        }
    }

    private static final class FakeStoragePort implements DriveObjectStoragePort {
        private final List<PrepareObject> prepared = new ArrayList<>();
        private final List<CompleteObject> completed = new ArrayList<>();
        private final List<UUID> deletedObjects = new ArrayList<>();

        @Override
        public PreparedObject prepareUpload(PrepareObject command) {
            prepared.add(command);
            return new PreparedObject(uuid(101), uuid(102), uuid(103), NOW.plusSeconds(900));
        }

        @Override
        public StoredObject completeUpload(CompleteObject command) {
            completed.add(command);
            return new StoredObject(command.objectId(), command.versionId(), "");
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
