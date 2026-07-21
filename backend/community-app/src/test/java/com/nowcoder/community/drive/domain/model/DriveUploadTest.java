package com.nowcoder.community.drive.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class DriveUploadTest {

    private static final Instant NOW = Instant.parse("2026-05-09T00:00:00Z");

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
    void completeFinalizationShouldBeIdempotentForAlreadyCompletedUpload() {
        UUID entryId = uuid(90);
        DriveUpload completed = preparedUpload()
                .startCompleting(entryId, NOW.plusSeconds(1))
                .markObjectCompleted(NOW.plusSeconds(2))
                .completeFinalization(NOW.plusSeconds(3));

        DriveUpload retried = completed.completeFinalization(NOW.plusSeconds(4));

        assertThat(retried.status()).isEqualTo(DriveUploadStatus.COMPLETED);
        assertThat(retried.completedEntryId()).isEqualTo(entryId);
        assertThat(retried.completedAt()).isEqualTo(NOW.plusSeconds(3));
        assertThat(retried.updatedAt()).isEqualTo(NOW.plusSeconds(3));
    }

    @Test
    void checksumShouldRemainStableAcrossCompletionStateTransitions() {
        UUID entryId = uuid(90);
        DriveUpload prepared = preparedUpload("sha256:abc");
        DriveUpload completing = prepared.startCompleting(entryId, NOW.plusSeconds(1));
        DriveUpload objectCompleted = completing.markObjectCompleted(NOW.plusSeconds(2));
        DriveUpload completed = objectCompleted.completeFinalization(NOW.plusSeconds(3));

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
