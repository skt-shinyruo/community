package com.nowcoder.community.drive.domain.model;

import com.nowcoder.community.drive.domain.service.DriveEntryDomainService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriveEntryTest {

    @Test
    void folderAndFileShouldNormalizeNamesAndStatuses() {
        Instant now = Instant.parse("2026-05-09T00:00:00Z");
        DriveEntry folder = DriveEntry.folder(uuid(1), uuid(2), null, " Docs ", now);
        DriveEntry file = DriveEntry.file(uuid(3), uuid(2), folder.entryId(), " a.txt ", uuid(4), uuid(5), 8, "text/plain", now);

        assertThat(folder.name()).isEqualTo("Docs");
        assertThat(folder.type()).isEqualTo(DriveEntryType.FOLDER);
        assertThat(folder.status()).isEqualTo(DriveEntryStatus.ACTIVE);
        assertThat(file.name()).isEqualTo("a.txt");
        assertThat(file.type()).isEqualTo(DriveEntryType.FILE);
        assertThat(file.sizeBytes()).isEqualTo(8);
    }

    @Test
    void trashedEntryShouldRejectRenameUntilRestored() {
        DriveEntry file = DriveEntry.file(uuid(3), uuid(2), null, "a.txt", uuid(4), uuid(5), 8, "text/plain", Instant.parse("2026-05-09T00:00:00Z"))
                .trash(Instant.parse("2026-05-09T00:01:00Z"), Instant.parse("2026-06-08T00:01:00Z"));

        assertThat(file.status()).isEqualTo(DriveEntryStatus.TRASHED);
        assertThatThrownBy(() -> file.rename("b.txt", Instant.parse("2026-05-09T00:02:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("trashed entry cannot be changed");
    }

    @Test
    void domainServiceShouldRejectMovingFolderIntoDescendant() {
        DriveEntryDomainService service = new DriveEntryDomainService();
        UUID folderId = uuid(10);
        UUID childId = uuid(11);
        UUID grandChildId = uuid(12);

        assertThatThrownBy(() -> service.assertCanMove(folderId, grandChildId, List.of(childId, grandChildId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("folder cannot be moved into itself or descendant");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
