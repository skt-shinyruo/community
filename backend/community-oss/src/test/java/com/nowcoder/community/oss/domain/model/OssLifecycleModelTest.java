package com.nowcoder.community.oss.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OssLifecycleModelTest {

    private static final Instant NOW = Instant.parse("2026-05-07T00:00:00Z");

    @Test
    void objectVersionSessionAndReferenceShouldMoveThroughLifecycleStates() {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        UUID sessionId = uuid(3);
        UUID referenceId = uuid(4);
        OssObject object = OssObject.stage(
                objectId,
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                "7",
                OssVisibility.PUBLIC,
                "7",
                NOW
        );
        OssObjectVersion version = OssObjectVersion.staged(
                versionId,
                objectId,
                "S3_COMPATIBLE",
                "community-oss",
                "objects/1/2/avatar.png",
                "avatar.png",
                "image/png",
                6,
                "sha256-avatar",
                NOW
        );
        OssUploadSession session = OssUploadSession.ready(
                sessionId,
                objectId,
                versionId,
                "proxy",
                "community-app",
                "user",
                "avatar",
                "7",
                "avatar.png",
                "image/png",
                6,
                "sha256-avatar",
                "7",
                NOW,
                NOW.plusSeconds(900)
        );

        OssObjectVersion activeVersion = version.withUploadedContent("image/png", 6, "sha256-avatar")
                .activate("etag-1", NOW.plusSeconds(1));
        OssObject activeObject = object.activate(activeVersion, NOW.plusSeconds(1));
        OssUploadSession completedSession = session.complete(NOW.plusSeconds(2));
        OssObjectReference activeReference = OssObjectReference.active(
                referenceId,
                objectId,
                versionId,
                "community-app",
                "user",
                "avatar",
                "7",
                "profile-image",
                NOW.plusSeconds(3),
                NOW.plusSeconds(3600)
        );
        OssObjectReference releasedReference = activeReference.release(NOW.plusSeconds(4));
        OssObjectReference replayedRelease = releasedReference.release(NOW.plusSeconds(40));
        OssObject deletePendingObject = activeObject.deletePending(NOW.plusSeconds(5));
        OssObject purgedObject = deletePendingObject.purge(NOW.plusSeconds(6));
        OssObjectVersion purgedVersion = activeVersion.purge(NOW.plusSeconds(7));

        assertThat(object.status()).isEqualTo(OssObjectStatus.STAGED);
        assertThat(activeVersion.status()).isEqualTo(OssObjectVersionStatus.ACTIVE);
        assertThat(activeObject.status()).isEqualTo(OssObjectStatus.ACTIVE);
        assertThat(activeObject.currentVersionId()).isEqualTo(versionId);
        assertThat(completedSession.status()).isEqualTo(OssUploadSessionStatus.COMPLETED);
        assertThat(completedSession.completedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(session.expiredAt(NOW.plusSeconds(901))).isTrue();
        assertThat(activeReference.activeAt(NOW.plusSeconds(3))).isTrue();
        assertThat(activeReference.status()).isEqualTo(OssObjectReferenceStatus.ACTIVE);
        assertThat(releasedReference.status()).isEqualTo(OssObjectReferenceStatus.RELEASED);
        assertThat(releasedReference.releasedAt()).isEqualTo(NOW.plusSeconds(4));
        assertThat(replayedRelease).isSameAs(releasedReference);
        assertThat(replayedRelease.releasedAt()).isEqualTo(NOW.plusSeconds(4));
        assertThat(deletePendingObject.status()).isEqualTo(OssObjectStatus.DELETE_PENDING);
        assertThat(purgedObject.status()).isEqualTo(OssObjectStatus.PURGED);
        assertThat(purgedVersion.status()).isEqualTo(OssObjectVersionStatus.PURGED);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
