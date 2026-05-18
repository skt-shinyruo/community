package com.nowcoder.community.drive.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.drive.application.command.CreateDriveFolderCommand;
import com.nowcoder.community.drive.application.command.CreateDriveShareCommand;
import com.nowcoder.community.drive.application.command.VerifyDriveShareCommand;
import com.nowcoder.community.drive.application.result.DriveDownloadUrlResult;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
import com.nowcoder.community.drive.application.result.DrivePublicShareGateResult;
import com.nowcoder.community.drive.application.result.DriveShareResult;
import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import com.nowcoder.community.drive.infrastructure.security.BCryptDrivePasswordHasher;
import com.nowcoder.community.drive.infrastructure.security.HmacDriveShareTicketCodec;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriveShareApplicationServiceTest {

    @Test
    void createShareShouldRequirePasswordAndFutureExpiry() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveShareApplicationService service = fixture.shareService();
        UUID userId = uuid(7);
        UUID entryId = fixture.createFile(userId, "a.txt", 8);

        assertThatThrownBy(() -> service.createShare(new CreateDriveShareCommand(
                userId,
                entryId,
                "",
                Instant.parse("2026-05-10T00:00:00Z")
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("提取码错误");
        assertThatThrownBy(() -> service.createShare(new CreateDriveShareCommand(
                userId,
                entryId,
                "1234",
                Instant.parse("2026-05-08T23:59:59Z")
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("分享链接不可用");
    }

    @Test
    void shareVerificationShouldRequirePasswordAndRecordEveryAttempt() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveShareApplicationService service = fixture.shareService();
        UUID userId = uuid(7);
        UUID entryId = fixture.createFile(userId, "a.txt", 8);
        DriveShareResult share = service.createShare(new CreateDriveShareCommand(
                userId,
                entryId,
                "1234",
                Instant.parse("2026-05-10T00:00:00Z")
        ));

        assertThatThrownBy(() -> service.verifyShare(new VerifyDriveShareCommand(share.shareToken(), "bad", "ip:1")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("提取码错误");
        DriveShareResult verified = service.verifyShare(new VerifyDriveShareCommand(share.shareToken(), "1234", "ip:1"));

        assertThat(verified.ticket()).isNotBlank();
        assertThat(verified.ticketExpiresAt()).isEqualTo(TestDriveFixture.NOW.plusSeconds(600));
        assertThat(fixture.shareAccesses().records()).hasSize(2);
        assertThat(fixture.shareAccesses().records()).extracting(TestDriveFixture.AccessRecord::success)
                .containsExactly(false, true);
        assertThat(fixture.ticketCodec().issued()).hasSize(1);
    }

    @Test
    void verifyShareShouldRecordFailureWhenSharedEntryIsNoLongerActive() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveShareApplicationService service = fixture.shareService();
        DriveTrashApplicationService trashService = fixture.trashService();
        UUID userId = uuid(7);
        UUID entryId = fixture.createFile(userId, "a.txt", 8);
        DriveShareResult share = service.createShare(new CreateDriveShareCommand(
                userId,
                entryId,
                "1234",
                Instant.parse("2026-05-10T00:00:00Z")
        ));

        trashService.trash(userId, entryId);

        assertThatThrownBy(() -> service.verifyShare(new VerifyDriveShareCommand(share.shareToken(), "1234", "ip:1")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分享链接不可用");
        assertThat(fixture.shareAccesses().records()).hasSize(1);
        assertThat(fixture.shareAccesses().records()).extracting(TestDriveFixture.AccessRecord::success)
                .containsExactly(false);
    }

    @Test
    void folderShareShouldIssueDownloadUrlForDescendantFileAfterVerification() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveShareApplicationService service = fixture.shareService();
        DriveEntryApplicationService entryService = fixture.entryService();
        UUID userId = uuid(7);
        DriveEntryResult folder = entryService.createFolder(new CreateDriveFolderCommand(userId, null, "Folder"));
        UUID nestedFileId = fixture.createFile(userId, folder.entryId(), "child.txt", 8);
        UUID otherFileId = fixture.createFile(userId, null, "outside.txt", 8);
        DriveShareResult share = service.createShare(new CreateDriveShareCommand(
                userId,
                folder.entryId(),
                "1234",
                Instant.parse("2026-05-10T00:00:00Z")
        ));

        DriveShareResult verified = service.verifyShare(new VerifyDriveShareCommand(share.shareToken(), "1234", "ip:1"));
        DriveDownloadUrlResult download = service.createShareDownloadUrl(share.shareToken(), verified.ticket(), nestedFileId);

        assertThat(download.url()).contains("https://cdn.example.test/");
        assertThat(fixture.storage().downloadObjectIds).containsExactly(fixture.entry(nestedFileId).objectId());
        assertThatThrownBy(() -> service.createShareDownloadUrl(share.shareToken(), verified.ticket(), otherFileId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分享链接不可用");
    }

    @Test
    void folderShareShouldListRootAndNestedChildrenAfterVerification() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveShareApplicationService service = fixture.shareService();
        DriveEntryApplicationService entryService = fixture.entryService();
        UUID userId = uuid(7);
        DriveEntryResult folder = entryService.createFolder(new CreateDriveFolderCommand(userId, null, "Folder"));
        DriveEntryResult nestedFolder = entryService.createFolder(new CreateDriveFolderCommand(userId, folder.entryId(), "Nested"));
        fixture.createFile(userId, folder.entryId(), "root-file.txt", 8);
        fixture.createFile(userId, nestedFolder.entryId(), "nested-file.txt", 8);
        DriveShareResult share = service.createShare(new CreateDriveShareCommand(
                userId,
                folder.entryId(),
                "1234",
                Instant.parse("2026-05-10T00:00:00Z")
        ));
        DriveShareResult verified = service.verifyShare(new VerifyDriveShareCommand(share.shareToken(), "1234", "ip:1"));

        List<DriveEntryResult> rootChildren = service.listShareEntries(share.shareToken(), verified.ticket(), null);
        List<DriveEntryResult> nestedChildren = service.listShareEntries(share.shareToken(), verified.ticket(), nestedFolder.entryId());

        assertThat(rootChildren).extracting(DriveEntryResult::name)
                .containsExactly("Nested", "root-file.txt");
        assertThat(nestedChildren).extracting(DriveEntryResult::name)
                .containsExactly("nested-file.txt");
    }

    @Test
    void folderShareListingShouldRejectInvalidTicketAndParentOutsideShareScope() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveShareApplicationService service = fixture.shareService();
        DriveEntryApplicationService entryService = fixture.entryService();
        UUID userId = uuid(7);
        DriveEntryResult sharedFolder = entryService.createFolder(new CreateDriveFolderCommand(userId, null, "Shared"));
        DriveEntryResult outsideFolder = entryService.createFolder(new CreateDriveFolderCommand(userId, null, "Outside"));
        DriveShareResult share = service.createShare(new CreateDriveShareCommand(
                userId,
                sharedFolder.entryId(),
                "1234",
                Instant.parse("2026-05-10T00:00:00Z")
        ));
        DriveShareResult verified = service.verifyShare(new VerifyDriveShareCommand(share.shareToken(), "1234", "ip:1"));

        assertThatThrownBy(() -> service.listShareEntries(share.shareToken(), "bad-ticket", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分享链接不可用");
        assertThatThrownBy(() -> service.listShareEntries(share.shareToken(), verified.ticket(), outsideFolder.entryId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分享链接不可用");
    }

    @Test
    void loadPublicShareGateShouldNotExposeEntryMetadataOrEntryStateBeforeVerification() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveShareApplicationService service = fixture.shareService();
        DriveTrashApplicationService trashService = fixture.trashService();
        UUID userId = uuid(7);
        UUID entryId = fixture.createFile(userId, "a.txt", 8);
        DriveShareResult share = service.createShare(new CreateDriveShareCommand(
                userId,
                entryId,
                "1234",
                Instant.parse("2026-05-10T00:00:00Z")
        ));

        trashService.trash(userId, entryId);

        DrivePublicShareGateResult loaded = service.loadPublicShareGate(share.shareToken());
        service.revokeShare(userId, share.shareId());

        assertThat(loaded.shareToken()).isEqualTo(share.shareToken());
        assertThat(loaded.requiresPassword()).isTrue();
        assertThat(fixture.storage().downloadObjectIds).isEmpty();
        assertThatThrownBy(() -> service.loadPublicShareGate(share.shareToken()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分享链接不可用");
    }

    @Test
    void createShareDownloadUrlShouldRequireTicketAndEntryMatch() {
        TestDriveFixture fixture = TestDriveFixture.create();
        DriveShareApplicationService service = fixture.shareService();
        UUID userId = uuid(7);
        UUID entryId = fixture.createFile(userId, "a.txt", 8);
        UUID otherEntryId = fixture.createFile(userId, "b.txt", 8);
        DriveShareResult share = service.createShare(new CreateDriveShareCommand(
                userId,
                entryId,
                "1234",
                Instant.parse("2026-05-10T00:00:00Z")
        ));

        assertThatThrownBy(() -> service.createShareDownloadUrl(share.shareToken(), "bad-ticket", entryId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分享链接不可用");
        DriveShareResult verified = service.verifyShare(new VerifyDriveShareCommand(share.shareToken(), "1234", "ip:1"));
        assertThatThrownBy(() -> service.createShareDownloadUrl(share.shareToken(), verified.ticket(), otherEntryId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分享链接不可用");
        DriveDownloadUrlResult download = service.createShareDownloadUrl(share.shareToken(), verified.ticket(), entryId);

        assertThat(download.url()).contains("https://cdn.example.test/");
        assertThat(fixture.storage().downloadObjectIds).hasSize(1);
    }

    @Test
    void securityAdaptersShouldHashPasswordsAndValidateHmacTickets() {
        BCryptDrivePasswordHasher passwordHasher = new BCryptDrivePasswordHasher();
        HmacDriveShareTicketCodec ticketCodec = new HmacDriveShareTicketCodec("test-secret");
        Instant expiresAt = TestDriveFixture.NOW.plusSeconds(600);

        String passwordHash = passwordHasher.hash("1234");
        String ticket = ticketCodec.issue("share-token", expiresAt);

        assertThat(passwordHash).isNotEqualTo("1234");
        assertThat(passwordHasher.matches("1234", passwordHash)).isTrue();
        assertThat(passwordHasher.matches("bad", passwordHash)).isFalse();
        assertThat(ticketCodec.valid("share-token", ticket, TestDriveFixture.NOW)).isTrue();
        assertThat(ticketCodec.valid("other-share", ticket, TestDriveFixture.NOW)).isFalse();
        assertThat(ticketCodec.valid("share-token", ticket, expiresAt)).isFalse();
    }
}
