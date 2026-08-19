package com.nowcoder.community.drive.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriveUploadTest {

    private static final Instant NOW = Instant.parse("2026-05-09T00:00:00Z");

    @Test
    void preparingShouldPersistIntentWithoutOssIdentifiersAndTransitionAtomically() {
        DriveUpload preparing = DriveUpload.preparing(
                uuid(1), uuid(2), null, "report.pdf", 1_024L, "application/pdf", "sha256:abc",
                uuid(6), NOW, NOW.plusSeconds(900)
        );

        assertThat(preparing.status()).isEqualTo(DriveUploadStatus.PREPARING);
        assertThat(preparing.objectId()).isNull();
        assertThat(preparing.versionId()).isNull();
        assertThat(preparing.ossSessionId()).isNull();

        DriveUpload prepared = preparing.markPrepared(
                uuid(3), uuid(4), uuid(5), NOW.plusSeconds(900), NOW.plusSeconds(1));

        assertThat(prepared.status()).isEqualTo(DriveUploadStatus.PREPARED);
        assertThat(prepared.matchesPrepared(uuid(3), uuid(4), uuid(5), NOW.plusSeconds(900))).isTrue();
    }

    @Test
    void startCompletingShouldPersistStableEntryIdBeforeObjectStorageCompletion() {
        DriveUpload upload = preparedUpload();
        UUID entryId = uuid(90);

        DriveUpload completing = upload.startCompleting(entryId, NOW.plusSeconds(1));

        assertThat(completing.status()).isEqualTo(DriveUploadStatus.COMPLETING);
        assertThat(completing.completedEntryId()).isEqualTo(entryId);
        assertThat(completing.completedAt()).isNull();
        assertThat(completing.updatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void expirationShouldBeExplicitAtTheOssDeadlineAndIdempotent() {
        DriveUpload upload = preparedUpload();

        assertThat(upload.expiredAt(NOW.plusSeconds(899))).isFalse();
        assertThat(upload.expiredAt(NOW.plusSeconds(900))).isTrue();
        assertThatThrownBy(() -> upload.expire(NOW.plusSeconds(899)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("upload has not expired");
        assertThat(upload.startCompleting(uuid(90), NOW.plusSeconds(900)).status())
                .isEqualTo(DriveUploadStatus.EXPIRED);

        DriveUpload expired = upload.expire(NOW.plusSeconds(900));

        assertThat(expired.status()).isEqualTo(DriveUploadStatus.EXPIRED);
        assertThat(expired.updatedAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(expired.expire(NOW.plusSeconds(901))).isSameAs(expired);
        assertThat(expired.startCompleting(uuid(90), NOW.plusSeconds(901))).isSameAs(expired);
        assertThatThrownBy(() -> expired.complete(NOW.plusSeconds(901)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completionRecoveryWindowShouldStartAtEachCompletionStateTransition() {
        DriveUpload completing = preparedUpload().startCompleting(uuid(90), NOW.plusSeconds(1));

        assertThat(completing.expiresAt()).isEqualTo(NOW.plusSeconds(3_601));
        assertThat(completing.expiredAt(NOW.plusSeconds(900))).isFalse();
        assertThat(completing.expiredAt(NOW.plusSeconds(3_600))).isFalse();
        assertThat(completing.expiredAt(NOW.plusSeconds(3_601))).isTrue();

        DriveUpload objectCompleted = completing.markObjectCompleted(NOW.plusSeconds(100));
        assertThat(objectCompleted.expiresAt()).isEqualTo(NOW.plusSeconds(3_700));
        assertThat(objectCompleted.expiredAt(NOW.plusSeconds(3_699))).isFalse();
        assertThat(objectCompleted.expiredAt(NOW.plusSeconds(3_700))).isTrue();
    }

    @Test
    void terminalCompletionStatesShouldNotExpire() {
        DriveUpload objectCompleted = preparedUpload()
                .startCompleting(uuid(90), NOW.plusSeconds(1))
                .markObjectCompleted(NOW.plusSeconds(2));
        DriveUpload cleanupPending = objectCompleted.startCleanup(NOW.plusSeconds(3));

        assertThat(objectCompleted.complete(NOW.plusSeconds(4)).expiredAt(NOW.plusSeconds(10_000)))
                .isFalse();
        assertThat(cleanupPending.expiredAt(NOW.plusSeconds(10_000)))
                .isFalse();
    }

    @Test
    void cleanupShouldRemainRecoverableUntilObjectDeletionCompletes() {
        DriveUpload objectCompleted = preparedUpload()
                .startCompleting(uuid(90), NOW.plusSeconds(1))
                .markObjectCompleted(NOW.plusSeconds(2));

        DriveUpload cleanupPending = objectCompleted.startCleanup(NOW.plusSeconds(3));
        DriveUpload failed = cleanupPending.completeCleanup(NOW.plusSeconds(4));

        assertThat(cleanupPending.status()).isEqualTo(DriveUploadStatus.CLEANUP_PENDING);
        assertThat(cleanupPending.objectId()).isEqualTo(objectCompleted.objectId());
        assertThat(failed.status()).isEqualTo(DriveUploadStatus.FAILED);
        assertThat(failed.updatedAt()).isEqualTo(NOW.plusSeconds(4));
    }

    @Test
    void markObjectCompletedShouldKeepStableEntryIdForRecoveryFinalization() {
        UUID entryId = uuid(90);
        DriveUpload completing = preparedUpload().startCompleting(entryId, NOW.plusSeconds(1));

        DriveUpload objectCompleted = completing.markObjectCompleted(NOW.plusSeconds(2));

        assertThat(objectCompleted.status()).isEqualTo(DriveUploadStatus.OBJECT_COMPLETED);
        assertThat(objectCompleted.completedEntryId()).isEqualTo(entryId);
        assertThat(objectCompleted.completedAt()).isNull();
        assertThat(objectCompleted.updatedAt()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void completeShouldOnlyFinalizeObjectCompletedAndRemainIdempotent() {
        UUID entryId = uuid(90);
        DriveUpload completed = preparedUpload()
                .startCompleting(entryId, NOW.plusSeconds(1))
                .markObjectCompleted(NOW.plusSeconds(2))
                .complete(NOW.plusSeconds(3));

        DriveUpload retried = completed.complete(NOW.plusSeconds(4));

        assertThat(retried.status()).isEqualTo(DriveUploadStatus.COMPLETED);
        assertThat(retried.completedEntryId()).isEqualTo(entryId);
        assertThat(retried.completedAt()).isEqualTo(NOW.plusSeconds(3));
        assertThat(retried.updatedAt()).isEqualTo(NOW.plusSeconds(3));
        assertThat(retried).isSameAs(completed);
        assertThatThrownBy(() -> preparedUpload().complete(NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void checksumShouldRemainStableAcrossCompletionStateTransitions() {
        UUID entryId = uuid(90);
        DriveUpload prepared = preparedUpload("sha256:abc");
        DriveUpload completing = prepared.startCompleting(entryId, NOW.plusSeconds(1));
        DriveUpload objectCompleted = completing.markObjectCompleted(NOW.plusSeconds(2));
        DriveUpload completed = objectCompleted.complete(NOW.plusSeconds(3));

        assertThat(prepared.checksumSha256()).isEqualTo("sha256:abc");
        assertThat(completing.checksumSha256()).isEqualTo("sha256:abc");
        assertThat(objectCompleted.checksumSha256()).isEqualTo("sha256:abc");
        assertThat(completed.checksumSha256()).isEqualTo("sha256:abc");
    }

    private static DriveUpload preparedUpload() {
        return preparedUpload("");
    }

    private static DriveUpload preparedUpload(String checksumSha256) {
        return DriveUpload.prepared(
                uuid(1),
                uuid(2),
                null,
                "report.pdf",
                1_024L,
                "application/pdf",
                checksumSha256,
                uuid(3),
                uuid(4),
                uuid(5),
                uuid(6),
                NOW,
                NOW.plusSeconds(900)
        );
    }
}
