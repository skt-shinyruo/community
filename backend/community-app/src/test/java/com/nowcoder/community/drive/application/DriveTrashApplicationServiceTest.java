package com.nowcoder.community.drive.application;

import com.nowcoder.community.drive.application.command.CreateDriveFolderCommand;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import com.nowcoder.community.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
