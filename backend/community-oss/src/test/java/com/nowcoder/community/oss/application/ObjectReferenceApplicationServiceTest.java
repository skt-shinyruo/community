package com.nowcoder.community.oss.application;

import com.nowcoder.community.oss.application.command.BindObjectReferenceCommand;
import com.nowcoder.community.oss.application.command.ReleaseObjectReferenceCommand;
import com.nowcoder.community.oss.application.result.ObjectReferenceResult;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectReference;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssVisibility;
import com.nowcoder.community.oss.domain.repository.OssObjectReferenceRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectReferenceApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-07T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void bindAndReleaseShouldPersistReferenceLifecycle() {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeReferenceRepository referenceRepository = new FakeReferenceRepository();
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
                CLOCK.instant()
        ).activate("etag-1", CLOCK.instant());
        objectRepository.save(OssObject.stage(
                objectId,
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                "7",
                OssVisibility.PUBLIC,
                "7",
                CLOCK.instant()
        ).activate(version, CLOCK.instant()));
        FakeVersionRepository versionRepository = new FakeVersionRepository();
        versionRepository.save(version);
        ObjectReferenceApplicationService service = new ObjectReferenceApplicationService(
                objectRepository,
                versionRepository,
                referenceRepository,
                CLOCK
        );

        ObjectReferenceResult bound = service.bindReference(new BindObjectReferenceCommand(
                objectId,
                null,
                "community-app",
                "user",
                "avatar",
                "7",
                "PRIMARY",
                CLOCK.instant().plusSeconds(3600),
                "7"
        ));
        ObjectReferenceResult released = service.releaseReference(new ReleaseObjectReferenceCommand(
                objectId,
                bound.referenceId(),
                "7"
        ));

        assertThat(bound.status()).isEqualTo("ACTIVE");
        assertThat(referenceRepository.findByObjectId(objectId)).hasSize(1);
        assertThat(released.status()).isEqualTo("RELEASED");
        assertThat(released.releasedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void bindReferenceShouldRejectVersionFromDifferentObject() {
        UUID objectId = uuid(1);
        UUID otherObjectId = uuid(3);
        UUID versionId = uuid(2);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeVersionRepository versionRepository = new FakeVersionRepository();
        FakeReferenceRepository referenceRepository = new FakeReferenceRepository();
        objectRepository.save(OssObject.stage(
                objectId,
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                "7",
                OssVisibility.PUBLIC,
                "7",
                CLOCK.instant()
        ).activate(activeVersion(objectId, versionId), CLOCK.instant()));
        versionRepository.save(activeVersion(otherObjectId, versionId));
        ObjectReferenceApplicationService service = new ObjectReferenceApplicationService(
                objectRepository,
                versionRepository,
                referenceRepository,
                CLOCK
        );

        assertThatThrownBy(() -> service.bindReference(new BindObjectReferenceCommand(
                objectId,
                versionId,
                "community-app",
                "user",
                "avatar",
                "7",
                "PRIMARY",
                null,
                "7"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("object version does not belong to object");
    }

    private static OssObjectVersion activeVersion(UUID objectId, UUID versionId) {
        return OssObjectVersion.staged(
                versionId,
                objectId,
                "S3_COMPATIBLE",
                "community-oss",
                "objects/1/2/avatar.png",
                "avatar.png",
                "image/png",
                6,
                "sha256-avatar",
                CLOCK.instant()
        ).activate("etag-1", CLOCK.instant());
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static final class FakeObjectRepository implements OssObjectRepository {
        private final Map<UUID, OssObject> rows = new HashMap<>();

        @Override
        public void save(OssObject object) {
            rows.put(object.objectId(), object);
        }

        @Override
        public Optional<OssObject> findById(UUID objectId) {
            return Optional.ofNullable(rows.get(objectId));
        }
    }

    private static final class FakeReferenceRepository implements OssObjectReferenceRepository {
        private final Map<UUID, OssObjectReference> rows = new HashMap<>();

        @Override
        public void save(OssObjectReference reference) {
            rows.put(reference.referenceId(), reference);
        }

        @Override
        public Optional<OssObjectReference> findById(UUID referenceId) {
            return Optional.ofNullable(rows.get(referenceId));
        }

        @Override
        public List<OssObjectReference> findByObjectId(UUID objectId) {
            return rows.values().stream().filter(reference -> objectId.equals(reference.objectId())).toList();
        }
    }

    private static final class FakeVersionRepository implements OssObjectVersionRepository {
        private final Map<UUID, OssObjectVersion> rows = new HashMap<>();

        @Override
        public void save(OssObjectVersion version) {
            rows.put(version.versionId(), version);
        }

        @Override
        public Optional<OssObjectVersion> findById(UUID versionId) {
            return Optional.ofNullable(rows.get(versionId));
        }
    }
}
