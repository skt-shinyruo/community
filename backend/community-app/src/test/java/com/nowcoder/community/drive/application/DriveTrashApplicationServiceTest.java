package com.nowcoder.community.drive.application;

import com.nowcoder.community.drive.application.command.CreateDriveFolderCommand;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriveTrashApplicationServiceTest {

    @Test
    void trashRestoreAndPermanentDeleteShouldControlQuota() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveTrashApplicationService trashService = fixture.trashService();
        UUID userId = uuid(7);
        UUID fileId = fixture.createFile(userId, "a.txt", 8);

        trashService.trash(userId, fileId);
        assertThat(fixture.space(userId).usedBytes()).isEqualTo(8);
        trashService.restore(userId, fileId, null);
        assertThat(fixture.entry(fileId).status()).isEqualTo(DriveEntryStatus.ACTIVE);
        trashService.trash(userId, fileId);
        trashService.deletePermanently(userId, fileId);

        assertThat(fixture.space(userId).usedBytes()).isZero();
        assertThat(fixture.entry(fileId).status()).isEqualTo(DriveEntryStatus.DELETED);
        assertThat(fixture.storage().deletedObjects).hasSize(1);
    }

    @Test
    void listTrashShouldCreateDefaultSpaceForFirstAccess() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveTrashApplicationService trashService = fixture.trashService();
        UUID userId = uuid(7);

        assertThat(trashService.listTrash(userId)).isEmpty();
        assertThat(fixture.space(userId).quotaBytes()).isEqualTo(10_737_418_240L);
    }

    @Test
    void trashAndPermanentDeleteShouldApplyRecursively() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveEntryApplicationService entryService = fixture.entryService();
        DriveTrashApplicationService trashService = fixture.trashService();
        UUID userId = uuid(7);
        DriveEntryResult folder = entryService.createFolder(new CreateDriveFolderCommand(userId, null, "Folder"));
        DriveEntryResult childFolder = entryService.createFolder(new CreateDriveFolderCommand(userId, folder.entryId(), "Child"));
        UUID rootFileId = fixture.createFile(userId, folder.entryId(), "root.txt", 8);
        UUID childFileId = fixture.createFile(userId, childFolder.entryId(), "child.txt", 12);

        trashService.trash(userId, folder.entryId());

        assertThat(fixture.entry(folder.entryId()).status()).isEqualTo(DriveEntryStatus.TRASHED);
        assertThat(fixture.entry(childFolder.entryId()).status()).isEqualTo(DriveEntryStatus.TRASHED);
        assertThat(fixture.entry(rootFileId).status()).isEqualTo(DriveEntryStatus.TRASHED);
        assertThat(fixture.entry(childFileId).status()).isEqualTo(DriveEntryStatus.TRASHED);
        assertThat(fixture.space(userId).usedBytes()).isEqualTo(20);
        assertThat(trashService.listTrash(userId)).extracting(DriveEntryResult::entryId)
                .contains(folder.entryId(), childFolder.entryId(), rootFileId, childFileId);

        trashService.deletePermanently(userId, folder.entryId());

        assertThat(fixture.entry(folder.entryId()).status()).isEqualTo(DriveEntryStatus.DELETED);
        assertThat(fixture.entry(childFolder.entryId()).status()).isEqualTo(DriveEntryStatus.DELETED);
        assertThat(fixture.entry(rootFileId).status()).isEqualTo(DriveEntryStatus.DELETED);
        assertThat(fixture.entry(childFileId).status()).isEqualTo(DriveEntryStatus.DELETED);
        assertThat(fixture.space(userId).usedBytes()).isZero();
        assertThat(fixture.storage().deletedObjects).hasSize(2);
    }

    @Test
    void restoreFolderShouldRestoreDescendants() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveEntryApplicationService entryService = fixture.entryService();
        DriveTrashApplicationService trashService = fixture.trashService();
        UUID userId = uuid(7);
        DriveEntryResult folder = entryService.createFolder(new CreateDriveFolderCommand(userId, null, "Folder"));
        DriveEntryResult childFolder = entryService.createFolder(new CreateDriveFolderCommand(userId, folder.entryId(), "Child"));
        UUID childFileId = fixture.createFile(userId, childFolder.entryId(), "child.txt", 12);

        trashService.trash(userId, folder.entryId());
        trashService.restore(userId, folder.entryId(), null);

        assertThat(fixture.entry(folder.entryId()).status()).isEqualTo(DriveEntryStatus.ACTIVE);
        assertThat(fixture.entry(childFolder.entryId()).status()).isEqualTo(DriveEntryStatus.ACTIVE);
        assertThat(fixture.entry(childFileId).status()).isEqualTo(DriveEntryStatus.ACTIVE);
    }

    @Test
    void restoreShouldNotReanimateDescendantsTrashedBeforeParent() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveEntryApplicationService entryService = fixture.entryService();
        DriveTrashApplicationService trashService = fixture.trashService();
        UUID userId = uuid(7);
        DriveEntryResult folder = entryService.createFolder(new CreateDriveFolderCommand(userId, null, "Folder"));
        UUID childFileId = fixture.createFile(userId, folder.entryId(), "child.txt", 12);

        trashService.trash(userId, childFileId);
        trashService.trash(userId, folder.entryId());
        trashService.restore(userId, folder.entryId(), null);

        assertThat(fixture.entry(folder.entryId()).status()).isEqualTo(DriveEntryStatus.ACTIVE);
        assertThat(fixture.entry(childFileId).status()).isEqualTo(DriveEntryStatus.TRASHED);
    }

    @Test
    void restoreShouldRejectNonRootTrashedEntry() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveEntryApplicationService entryService = fixture.entryService();
        DriveTrashApplicationService trashService = fixture.trashService();
        UUID userId = uuid(7);
        DriveEntryResult folder = entryService.createFolder(new CreateDriveFolderCommand(userId, null, "Folder"));
        UUID childFileId = fixture.createFile(userId, folder.entryId(), "child.txt", 12);

        trashService.trash(userId, folder.entryId());

        assertThatThrownBy(() -> trashService.restore(userId, childFileId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("回收站条目不可执行该操作");
        assertThat(fixture.entry(folder.entryId()).status()).isEqualTo(DriveEntryStatus.TRASHED);
        assertThat(fixture.entry(childFileId).status()).isEqualTo(DriveEntryStatus.TRASHED);
    }

    @Test
    void deletePermanentlyShouldNotDoubleDeleteAlreadyDeletedDescendants() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveEntryApplicationService entryService = fixture.entryService();
        DriveTrashApplicationService trashService = fixture.trashService();
        UUID userId = uuid(7);
        DriveEntryResult folder = entryService.createFolder(new CreateDriveFolderCommand(userId, null, "Folder"));
        UUID childFileId = fixture.createFile(userId, folder.entryId(), "child.txt", 8);

        trashService.trash(userId, childFileId);
        trashService.deletePermanently(userId, childFileId);
        trashService.trash(userId, folder.entryId());
        trashService.deletePermanently(userId, folder.entryId());

        assertThat(fixture.entry(childFileId).status()).isEqualTo(DriveEntryStatus.DELETED);
        assertThat(fixture.storage().deletedObjects).hasSize(1);
    }

    @Test
    void permanentDeleteShouldRejectActiveEntry() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveTrashApplicationService trashService = fixture.trashService();
        UUID userId = uuid(7);
        UUID fileId = fixture.createFile(userId, "a.txt", 8);

        assertThatThrownBy(() -> trashService.deletePermanently(userId, fileId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("回收站条目不可执行该操作");
        assertThat(fixture.entry(fileId).status()).isEqualTo(DriveEntryStatus.ACTIVE);
        assertThat(fixture.space(userId).usedBytes()).isEqualTo(8);
        assertThat(fixture.storage().deletedObjects).isEmpty();
    }

    @Test
    void permanentDeleteShouldLockAndRereadInsideRequiresNewBeforeCleanup() {
        UUID userId = uuid(7);
        UUID spaceId = uuid(80);
        UUID folderId = uuid(81);
        UUID fileId = uuid(82);
        UUID objectId = uuid(83);
        Instant now = Instant.parse("2026-05-09T00:00:00Z");
        DriveSpace snapshot = usedSpace(spaceId, userId, 16, now.minusSeconds(1));
        DriveSpace locked = usedSpace(spaceId, userId, 8, now);
        DriveEntry folder = DriveEntry.folder(folderId, spaceId, null, "folder", now)
                .trash(now, now.plusSeconds(86_400));
        DriveEntry file = DriveEntry.file(fileId, spaceId, folderId, "a.txt", objectId, uuid(84), 8, "text/plain", now)
                .trash(folderId, now, now.plusSeconds(86_400));
        DriveSpaceRepository spaceRepository = mock(DriveSpaceRepository.class);
        DriveEntryRepository entryRepository = mock(DriveEntryRepository.class);
        DriveObjectStoragePort storagePort = mock(DriveObjectStoragePort.class);
        List<String> calls = new ArrayList<>();
        AtomicReference<DriveSpace> savedSpace = new AtomicReference<>();

        when(spaceRepository.findByUserId(userId)).thenAnswer(invocation -> {
            calls.add("find-space");
            return Optional.of(snapshot);
        });
        when(spaceRepository.lockById(spaceId)).thenAnswer(invocation -> {
            calls.add("lock-space");
            return locked;
        });
        when(spaceRepository.findById(spaceId)).thenAnswer(invocation -> {
            calls.add("find-latest-space");
            return Optional.of(locked);
        });
        when(entryRepository.findById(spaceId, folderId)).thenAnswer(invocation -> {
            calls.add("find-root");
            return Optional.of(folder);
        });
        when(entryRepository.listDescendantIds(spaceId, folderId)).thenAnswer(invocation -> {
            calls.add("list-descendants");
            return List.of(fileId);
        });
        when(entryRepository.findById(spaceId, fileId)).thenAnswer(invocation -> {
            calls.add("find-file");
            return Optional.of(file);
        });
        when(entryRepository.markDeletedIfTrashed(any(DriveEntry.class))).thenAnswer(invocation -> {
            DriveEntry deleted = invocation.getArgument(0);
            calls.add("cas-" + (deleted.folder() ? "folder" : "file"));
            assertThat(deleted.status()).isEqualTo(DriveEntryStatus.DELETED);
            return true;
        });
        doAnswer(invocation -> {
            calls.add("save-entry");
            return null;
        }).when(entryRepository).save(any(DriveEntry.class));
        doAnswer(invocation -> {
            calls.add("save-space");
            savedSpace.set(invocation.getArgument(0));
            return null;
        }).when(spaceRepository).save(any(DriveSpace.class));
        doAnswer(invocation -> {
            calls.add("delete-object");
            return null;
        }).when(storagePort).deleteObject(objectId, userId.toString());
        DriveTransactionOperations operations = recordingTransactions(calls);
        DriveTrashApplicationService service = new DriveTrashApplicationService(
                spaceRepository,
                entryRepository,
                storagePort,
                Clock.fixed(now, ZoneOffset.UTC),
                operations
        );

        service.deletePermanently(userId, folderId);

        assertThat(calls).containsExactly(
                "tx-start",
                "find-space",
                "lock-space",
                "find-root",
                "list-descendants",
                "find-file",
                "cas-folder",
                "cas-file",
                "save-space",
                "tx-end",
                "delete-object"
        );
        assertThat(savedSpace.get()).isEqualTo(locked.release(8, now));
    }

    @Test
    void permanentDeleteShouldNotReleaseQuotaWhenNoConditionalTransitionWins() {
        UUID userId = uuid(7);
        UUID spaceId = uuid(90);
        UUID fileId = uuid(91);
        UUID objectId = uuid(92);
        Instant now = Instant.parse("2026-05-09T00:00:00Z");
        DriveSpace space = usedSpace(spaceId, userId, 8, now);
        DriveEntry file = DriveEntry.file(fileId, spaceId, null, "a.txt", objectId, uuid(93), 8, "text/plain", now)
                .trash(now, now.plusSeconds(86_400));
        DriveSpaceRepository spaceRepository = mock(DriveSpaceRepository.class);
        DriveEntryRepository entryRepository = mock(DriveEntryRepository.class);
        DriveObjectStoragePort storagePort = mock(DriveObjectStoragePort.class);
        when(spaceRepository.findByUserId(userId)).thenReturn(Optional.of(space));
        when(spaceRepository.lockById(spaceId)).thenReturn(space);
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(entryRepository.findById(spaceId, fileId)).thenReturn(Optional.of(file));
        when(entryRepository.listDescendantIds(spaceId, fileId)).thenReturn(List.of());
        when(entryRepository.markDeletedIfTrashed(any(DriveEntry.class))).thenReturn(false);
        DriveTrashApplicationService service = new DriveTrashApplicationService(
                spaceRepository,
                entryRepository,
                storagePort,
                Clock.fixed(now, ZoneOffset.UTC),
                DirectDriveTransactionOperations.INSTANCE
        );

        service.deletePermanently(userId, fileId);

        verify(entryRepository).markDeletedIfTrashed(file.delete(now));
        verify(spaceRepository, never()).save(any(DriveSpace.class));
        verify(storagePort, never()).deleteObject(any(UUID.class), any(String.class));
    }

    @Test
    void permanentDeleteShouldReleaseOnlyFilesWhoseConditionalTransitionWins() {
        UUID userId = uuid(7);
        UUID spaceId = uuid(100);
        UUID folderId = uuid(101);
        UUID oldFileId = uuid(102);
        UUID winnerFileId = uuid(103);
        UUID oldObjectId = uuid(104);
        UUID winnerObjectId = uuid(105);
        Instant now = Instant.parse("2026-05-09T00:00:00Z");
        DriveSpace space = usedSpace(spaceId, userId, 20, now);
        DriveEntry folder = DriveEntry.folder(folderId, spaceId, null, "folder", now)
                .trash(now, now.plusSeconds(86_400));
        DriveEntry oldFile = DriveEntry.file(
                        oldFileId, spaceId, folderId, "old.txt", oldObjectId, uuid(106), 8, "text/plain", now.minusSeconds(10)
                )
                .trash(folderId, now.minusSeconds(10), now.plusSeconds(86_390))
                .delete(now.minusSeconds(5));
        DriveEntry winnerFile = DriveEntry.file(
                        winnerFileId, spaceId, folderId, "winner.txt", winnerObjectId, uuid(107), 12, "text/plain", now
                )
                .trash(folderId, now, now.plusSeconds(86_400));
        DriveSpaceRepository spaceRepository = mock(DriveSpaceRepository.class);
        DriveEntryRepository entryRepository = mock(DriveEntryRepository.class);
        DriveObjectStoragePort storagePort = mock(DriveObjectStoragePort.class);
        AtomicReference<DriveSpace> savedSpace = new AtomicReference<>();
        when(spaceRepository.findByUserId(userId)).thenReturn(Optional.of(space));
        when(spaceRepository.lockById(spaceId)).thenReturn(space);
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(entryRepository.findById(spaceId, folderId)).thenReturn(Optional.of(folder));
        when(entryRepository.listDescendantIds(spaceId, folderId)).thenReturn(List.of(oldFileId, winnerFileId));
        when(entryRepository.findById(spaceId, oldFileId)).thenReturn(Optional.of(oldFile));
        when(entryRepository.findById(spaceId, winnerFileId)).thenReturn(Optional.of(winnerFile));
        when(entryRepository.markDeletedIfTrashed(any(DriveEntry.class))).thenReturn(true);
        doAnswer(invocation -> {
            savedSpace.set(invocation.getArgument(0));
            return null;
        }).when(spaceRepository).save(any(DriveSpace.class));
        DriveTrashApplicationService service = new DriveTrashApplicationService(
                spaceRepository,
                entryRepository,
                storagePort,
                Clock.fixed(now, ZoneOffset.UTC),
                DirectDriveTransactionOperations.INSTANCE
        );

        service.deletePermanently(userId, folderId);

        verify(entryRepository, never()).markDeletedIfTrashed(oldFile);
        verify(entryRepository).markDeletedIfTrashed(folder.delete(now));
        verify(entryRepository).markDeletedIfTrashed(winnerFile.delete(now));
        assertThat(savedSpace.get()).isEqualTo(space.release(12, now));
        verify(storagePort, never()).deleteObject(oldObjectId, userId.toString());
        verify(storagePort).deleteObject(winnerObjectId, userId.toString());
    }

    private static DriveSpace usedSpace(UUID spaceId, UUID userId, long usedBytes, Instant now) {
        return DriveSpace.createDefault(spaceId, userId, now)
                .reserve(usedBytes, now)
                .commitReserved(usedBytes, now);
    }

    private static DriveTransactionOperations recordingTransactions(List<String> calls) {
        return new DriveTransactionOperations() {
            @Override
            public <T> T requiresNew(Supplier<T> action) {
                calls.add("tx-start");
                T result = action.get();
                calls.add("tx-end");
                return result;
            }
        };
    }
}
