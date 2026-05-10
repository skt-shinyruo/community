package com.nowcoder.community.drive.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.drive.application.command.CreateDriveShareCommand;
import com.nowcoder.community.drive.application.command.VerifyDriveShareCommand;
import com.nowcoder.community.drive.application.result.DriveDownloadUrlResult;
import com.nowcoder.community.drive.application.result.DriveShareResult;
import com.nowcoder.community.drive.infrastructure.security.BCryptDrivePasswordHasher;
import com.nowcoder.community.drive.infrastructure.security.HmacDriveShareTicketCodec;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
    void loadPublicShareAndRevokeShouldNotExposeVerifiedDownloadAccess() {
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

        DriveShareResult loaded = service.loadPublicShare(share.shareToken());
        service.revokeShare(userId, share.shareId());

        assertThat(loaded.entryId()).isEqualTo(entryId);
        assertThat(loaded.ticket()).isNull();
        assertThat(fixture.storage().downloadObjectIds).isEmpty();
        assertThatThrownBy(() -> service.loadPublicShare(share.shareToken()))
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
