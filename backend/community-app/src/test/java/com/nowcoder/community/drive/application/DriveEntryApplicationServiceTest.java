package com.nowcoder.community.drive.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.drive.application.command.CreateDriveFolderCommand;
import com.nowcoder.community.drive.application.command.MoveDriveEntryCommand;
import com.nowcoder.community.drive.application.command.RenameDriveEntryCommand;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.drive.application.result.DriveDownloadUrlResult;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
import com.nowcoder.community.drive.application.result.DriveSpaceResult;
import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriveEntryApplicationServiceTest {

    @Test
    void createFolderShouldLazilyCreateDefaultSpaceAndRejectDuplicateActiveSibling() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveEntryApplicationService service = fixture.entryService();
        UUID userId = uuid(7);

        DriveEntryResult docs = service.createFolder(new CreateDriveFolderCommand(userId, null, "Docs"));
        DriveSpaceResult space = fixture.spaceService().getSpace(userId);

        assertThat(docs.name()).isEqualTo("Docs");
        assertThat(space.quotaBytes()).isEqualTo(10_737_418_240L);
        assertThat(space.usedBytes()).isZero();
        assertThatThrownBy(() -> service.createFolder(new CreateDriveFolderCommand(userId, null, "Docs")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同名文件或文件夹已存在");
    }

    @Test
    void createFolderShouldRecoverFromDuplicateKeyDuringBootstrap() {
        DriveSpaceRepository spaceRepository = mock(DriveSpaceRepository.class);
        DriveEntryRepository entryRepository = mock(DriveEntryRepository.class);
        DriveObjectStoragePort storagePort = mock(DriveObjectStoragePort.class);
        Instant now = Instant.parse("2026-05-09T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        UUID userId = uuid(7);
        UUID existingSpaceId = uuid(8);
        DriveSpace existingSpace = DriveSpace.createDefault(existingSpaceId, userId, now);

        when(spaceRepository.findByUserId(userId))
                .thenReturn(Optional.empty(), Optional.of(existingSpace));
        doThrow(new DuplicateKeyException("duplicate drive_space user")).when(spaceRepository).save(any(DriveSpace.class));
        when(entryRepository.findActiveChildByName(any(), any(), any())).thenReturn(Optional.empty());

        DriveEntryApplicationService service = new DriveEntryApplicationService(spaceRepository, entryRepository, storagePort, clock);
        DriveEntryResult result = service.createFolder(new CreateDriveFolderCommand(userId, null, "Docs"));

        assertThat(result.name()).isEqualTo("Docs");
        verify(spaceRepository, times(2)).findByUserId(userId);
        ArgumentCaptor<DriveEntry> entryCaptor = ArgumentCaptor.forClass(DriveEntry.class);
        verify(entryRepository).save(entryCaptor.capture());
        assertThat(entryCaptor.getValue().spaceId()).isEqualTo(existingSpaceId);
    }

    @Test
    void listSearchRenameMoveAndDownloadShouldStayInsideActorSpace() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveEntryApplicationService service = fixture.entryService();
        UUID ownerId = uuid(7);
        UUID otherId = uuid(8);
        DriveEntryResult docs = service.createFolder(new CreateDriveFolderCommand(ownerId, null, "Docs"));
        DriveEntryResult work = service.createFolder(new CreateDriveFolderCommand(ownerId, null, "Work"));
        UUID fileId = fixture.createFile(ownerId, docs.entryId(), "a.txt", 8);
        fixture.createFile(otherId, null, "a.txt", 8);

        DriveEntryResult renamed = service.rename(new RenameDriveEntryCommand(ownerId, fileId, "b.txt"));
        DriveEntryResult moved = service.move(new MoveDriveEntryCommand(ownerId, fileId, work.entryId()));
        List<DriveEntryResult> workEntries = service.listEntries(ownerId, work.entryId());
        List<DriveEntryResult> searchResults = service.search(ownerId, "b.");
        DriveDownloadUrlResult download = service.createDownloadUrl(ownerId, fileId);

        assertThat(renamed.name()).isEqualTo("b.txt");
        assertThat(moved.parentId()).isEqualTo(work.entryId());
        assertThat(workEntries).extracting(DriveEntryResult::name).containsExactly("b.txt");
        assertThat(searchResults).extracting(DriveEntryResult::entryId).containsExactly(fileId);
        assertThat(download.url()).contains("https://cdn.example.test/");
        assertThat(service.search(otherId, "b.")).isEmpty();
    }

    @Test
    void renameAndMoveShouldRejectDuplicateActiveSiblingNames() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveEntryApplicationService service = fixture.entryService();
        UUID userId = uuid(7);
        DriveEntryResult docs = service.createFolder(new CreateDriveFolderCommand(userId, null, "Docs"));
        DriveEntryResult work = service.createFolder(new CreateDriveFolderCommand(userId, null, "Work"));
        UUID sourceFile = fixture.createFile(userId, docs.entryId(), "a.txt", 8);
        fixture.createFile(userId, docs.entryId(), "b.txt", 8);
        fixture.createFile(userId, work.entryId(), "a.txt", 8);

        assertThatThrownBy(() -> service.rename(new RenameDriveEntryCommand(userId, sourceFile, "b.txt")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同名文件或文件夹已存在");
        assertThatThrownBy(() -> service.move(new MoveDriveEntryCommand(userId, sourceFile, work.entryId())))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同名文件或文件夹已存在");
    }

    @Test
    void moveShouldRejectMovingFolderIntoSelfOrDescendant() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveEntryApplicationService service = fixture.entryService();
        UUID userId = uuid(7);
        DriveEntryResult folder = service.createFolder(new CreateDriveFolderCommand(userId, null, "Folder"));
        DriveEntryResult child = service.createFolder(new CreateDriveFolderCommand(userId, folder.entryId(), "Child"));

        assertThatThrownBy(() -> service.move(new MoveDriveEntryCommand(userId, folder.entryId(), folder.entryId())))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能移动到自身或子目录");
        assertThatThrownBy(() -> service.move(new MoveDriveEntryCommand(userId, folder.entryId(), child.entryId())))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能移动到自身或子目录");
    }

    @Test
    void moveShouldLockSpaceBeforeSavingTreeMutation() {
        UUID userId = uuid(7);
        Instant now = Instant.parse("2026-05-09T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        DriveObjectStoragePort storagePort = noOpStoragePort();
        DriveSpace space = DriveSpace.createDefault(uuid(70), userId, now);
        DriveEntry source = DriveEntry.folder(uuid(71), space.spaceId(), null, "Source", now.plusSeconds(1));
        DriveEntry target = DriveEntry.folder(uuid(72), space.spaceId(), null, "Target", now.plusSeconds(2));
        AtomicBoolean locked = new AtomicBoolean(false);

        DriveSpaceRepository spaceRepository = new DriveSpaceRepository() {
            @Override
            public Optional<DriveSpace> findByUserId(UUID queryUserId) {
                return userId.equals(queryUserId) ? Optional.of(space) : Optional.empty();
            }

            @Override
            public Optional<DriveSpace> findById(UUID spaceId) {
                return space.spaceId().equals(spaceId) ? Optional.of(space) : Optional.empty();
            }

            @Override
            public DriveSpace lockById(UUID spaceId) {
                if (!space.spaceId().equals(spaceId)) {
                    return null;
                }
                locked.set(true);
                return space;
            }

            @Override
            public boolean reserve(UUID spaceId, long bytes, Instant updatedAt) {
                return false;
            }

            @Override
            public boolean commitReserved(UUID spaceId, long bytes, Instant updatedAt) {
                return false;
            }

            @Override
            public boolean releaseReserved(UUID spaceId, long bytes, Instant updatedAt) {
                return false;
            }

            @Override
            public void save(DriveSpace ignored) {
            }
        };

        DriveEntryRepository entryRepository = new DriveEntryRepository() {
            @Override
            public Optional<DriveEntry> findById(UUID spaceId, UUID entryId) {
                if (!space.spaceId().equals(spaceId)) {
                    return Optional.empty();
                }
                if (source.entryId().equals(entryId)) {
                    return Optional.of(source);
                }
                if (target.entryId().equals(entryId)) {
                    return Optional.of(target);
                }
                return Optional.empty();
            }

            @Override
            public Optional<DriveEntry> findActiveChildByName(UUID spaceId, UUID parentId, String name) {
                return Optional.empty();
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
                assertThat(locked.get()).isTrue();
            }
        };

        DriveEntryApplicationService service = new DriveEntryApplicationService(spaceRepository, entryRepository, storagePort, clock);

        service.move(new MoveDriveEntryCommand(userId, source.entryId(), target.entryId()));

        assertThat(locked.get()).isTrue();
    }

    private static DriveObjectStoragePort noOpStoragePort() {
        return new DriveObjectStoragePort() {
            @Override
            public PreparedObject prepareUpload(PrepareObject command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StoredObject completeUpload(CompleteObject command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ObjectMetadata getMetadata(UUID objectId) {
                return null;
            }

            @Override
            public SignedDownloadUrl createDownloadUrl(UUID objectId, long ttlSeconds) {
                return null;
            }

            @Override
            public void deleteObject(UUID objectId, String actorId) {
            }
        };
    }

    @Test
    void createDownloadUrlShouldRejectFoldersAndTrashedFiles() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveEntryApplicationService service = fixture.entryService();
        DriveTrashApplicationService trashService = fixture.trashService();
        UUID userId = uuid(7);
        DriveEntryResult folder = service.createFolder(new CreateDriveFolderCommand(userId, null, "Folder"));
        UUID fileId = fixture.createFile(userId, null, "a.txt", 8);

        assertThatThrownBy(() -> service.createDownloadUrl(userId, folder.entryId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("网盘条目不存在");
        trashService.trash(userId, fileId);
        assertThat(fixture.entry(fileId).status()).isEqualTo(DriveEntryStatus.TRASHED);
        assertThatThrownBy(() -> service.createDownloadUrl(userId, fileId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("网盘条目不存在");
    }
}
