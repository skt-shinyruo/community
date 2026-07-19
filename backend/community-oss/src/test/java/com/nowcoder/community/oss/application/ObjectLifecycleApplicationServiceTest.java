package com.nowcoder.community.oss.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.oss.application.command.DeleteObjectCommand;
import com.nowcoder.community.oss.application.result.ObjectLifecycleResult;
import com.nowcoder.community.oss.domain.model.OssAccessGrant;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectReference;
import com.nowcoder.community.oss.domain.model.OssObjectStatus;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssVisibility;
import com.nowcoder.community.oss.domain.repository.OssAccessGrantRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectReferenceRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStore;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStoreObject;
import com.nowcoder.community.oss.infrastructure.storage.PresignedObjectUrl;
import com.nowcoder.community.oss.infrastructure.storage.StoredObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ObjectLifecycleApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-07T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void deleteObjectShouldMarkPendingWhenReferencesOrGrantsRemain() {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeVersionRepository versionRepository = new FakeVersionRepository();
        FakeReferenceRepository referenceRepository = new FakeReferenceRepository();
        FakeGrantRepository grantRepository = new FakeGrantRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        OssObjectVersion version = activeVersion(objectId, versionId);
        objectRepository.save(OssObject.stage(
                objectId,
                "USER_AVATAR",
                "community-app",
                "user",
                "USER",
                "7",
                OssVisibility.PUBLIC,
                "7",
                CLOCK.instant()
        ).activate(version, CLOCK.instant()));
        versionRepository.save(version);
        referenceRepository.save(OssObjectReference.active(
                uuid(3),
                objectId,
                versionId,
                "community-app",
                "user",
                "USER",
                "7",
                "PRIMARY",
                CLOCK.instant(),
                CLOCK.instant().plusSeconds(3600)
        ));
        ObjectLifecycleApplicationService service = new ObjectLifecycleApplicationService(
                objectRepository,
                versionRepository,
                referenceRepository,
                grantRepository,
                objectStore,
                CLOCK
        );

        ObjectLifecycleResult result = service.deleteObject(new DeleteObjectCommand(objectId, "7"));

        assertThat(result.deletePending()).isTrue();
        assertThat(result.purged()).isFalse();
        assertThat(objectRepository.findById(objectId)).get().extracting(OssObject::status)
                .isEqualTo(OssObjectStatus.DELETE_PENDING);
        assertThat(objectStore.deletedKey).isNull();
    }

    @Test
    void deleteObjectShouldPurgeBlobWhenNoDependenciesRemain() {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeVersionRepository versionRepository = new FakeVersionRepository();
        FakeReferenceRepository referenceRepository = new FakeReferenceRepository();
        FakeGrantRepository grantRepository = new FakeGrantRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        OssObjectVersion version = activeVersion(objectId, versionId);
        objectRepository.save(OssObject.stage(
                objectId,
                "USER_AVATAR",
                "community-app",
                "user",
                "USER",
                "7",
                OssVisibility.PUBLIC,
                "7",
                CLOCK.instant()
        ).activate(version, CLOCK.instant()));
        versionRepository.save(version);
        ObjectLifecycleApplicationService service = new ObjectLifecycleApplicationService(
                objectRepository,
                versionRepository,
                referenceRepository,
                grantRepository,
                objectStore,
                CLOCK
        );

        ObjectLifecycleResult result = service.deleteObject(new DeleteObjectCommand(objectId, "7"));

        assertThat(result.purged()).isTrue();
        assertThat(result.status()).isEqualTo("PURGED");
        assertThat(objectRepository.findById(objectId)).get().extracting(OssObject::status)
                .isEqualTo(OssObjectStatus.PURGED);
        assertThat(versionRepository.findById(versionId)).get().extracting(OssObjectVersion::status)
                .isEqualTo(com.nowcoder.community.oss.domain.model.OssObjectVersionStatus.PURGED);
        assertThat(objectStore.deletedBucket).isEqualTo("community-oss");
        assertThat(objectStore.deletedKey).isEqualTo("objects/1/2/avatar.png");
    }

    @Test
    void deleteObjectShouldHideMissingCurrentVersionBeforeDependencyMutation() {
        UUID objectId = uuid(30);
        UUID versionId = uuid(31);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeVersionRepository versionRepository = new FakeVersionRepository();
        FakeReferenceRepository referenceRepository = new FakeReferenceRepository();
        FakeGrantRepository grantRepository = new FakeGrantRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        OssObject object = OssObject.stage(
                objectId,
                "USER_AVATAR",
                "community-app",
                "user",
                "USER",
                "owner-7",
                OssVisibility.SIGNED,
                "owner-7",
                CLOCK.instant()
        ).activate(activeVersion(objectId, versionId), CLOCK.instant());
        OssObjectReference reference = OssObjectReference.active(
                uuid(32), objectId, versionId, "community-app", "user", "USER", "owner-7",
                "PRIMARY", CLOCK.instant(), CLOCK.instant().plusSeconds(3600));
        OssAccessGrant grant = OssAccessGrant.readGrant(
                uuid(33), objectId, null, "USER", "reader-8", "owner-7",
                CLOCK.instant(), CLOCK.instant().plusSeconds(3600));
        objectRepository.rows.put(objectId, object);
        referenceRepository.rows.put(reference.referenceId(), reference);
        grantRepository.rows.put(grant.grantId(), grant);
        ObjectLifecycleApplicationService service = new ObjectLifecycleApplicationService(
                objectRepository, versionRepository, referenceRepository,
                grantRepository, objectStore, CLOCK);

        Throwable failure = catchThrowable(() -> service.deleteObject(
                new DeleteObjectCommand(objectId, "owner-7")));

        assertHiddenObjectNotFound(failure);
        assertThat(objectRepository.findById(objectId)).contains(object);
        assertThat(objectRepository.saveCount).isZero();
        assertThat(versionRepository.saveCount).isZero();
        assertThat(referenceRepository.saveCount).isZero();
        assertThat(grantRepository.saveCount).isZero();
        assertThat(objectStore.deletedKey).isNull();
    }

    @Test
    void deleteObjectShouldHideCrossObjectCurrentVersionWithoutMutation() {
        UUID objectId = uuid(40);
        UUID versionId = uuid(41);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeVersionRepository versionRepository = new FakeVersionRepository();
        FakeReferenceRepository referenceRepository = new FakeReferenceRepository();
        FakeGrantRepository grantRepository = new FakeGrantRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        OssObject object = OssObject.stage(
                objectId,
                "USER_AVATAR",
                "community-app",
                "user",
                "USER",
                "owner-7",
                OssVisibility.SIGNED,
                "owner-7",
                CLOCK.instant()
        ).activate(activeVersion(objectId, versionId), CLOCK.instant());
        OssObjectVersion foreignVersion = activeVersion(uuid(42), versionId);
        objectRepository.rows.put(objectId, object);
        versionRepository.rows.put(versionId, foreignVersion);
        ObjectLifecycleApplicationService service = new ObjectLifecycleApplicationService(
                objectRepository, versionRepository, referenceRepository,
                grantRepository, objectStore, CLOCK);

        Throwable failure = catchThrowable(() -> service.deleteObject(
                new DeleteObjectCommand(objectId, "owner-7")));

        assertHiddenObjectNotFound(failure);
        assertThat(objectRepository.findById(objectId)).contains(object);
        assertThat(versionRepository.findById(versionId)).contains(foreignVersion);
        assertThat(objectRepository.saveCount).isZero();
        assertThat(versionRepository.saveCount).isZero();
        assertThat(referenceRepository.saveCount).isZero();
        assertThat(grantRepository.saveCount).isZero();
        assertThat(objectStore.deletedKey).isNull();
    }

    @Test
    void deleteObjectShouldHideObjectFromGrantUserAndUnrelatedUserWithoutMutation() {
        LifecycleFixture grantUserFixture = lifecycleFixture();
        grantUserFixture.grantRepository.save(OssAccessGrant.readGrant(
                uuid(20), grantUserFixture.objectId, null, "USER", "grant-user", "owner-7",
                CLOCK.instant().minusSeconds(60), CLOCK.instant().plusSeconds(300)));
        LifecycleFixture unrelatedFixture = lifecycleFixture();

        Throwable grantUser = catchThrowable(() -> grantUserFixture.service.deleteObject(
                new DeleteObjectCommand(grantUserFixture.objectId, "grant-user")));
        Throwable unrelated = catchThrowable(() -> unrelatedFixture.service.deleteObject(
                new DeleteObjectCommand(unrelatedFixture.objectId, "unrelated-user")));

        assertHiddenObjectNotFound(grantUser);
        assertHiddenObjectNotFound(unrelated);
        assertThat(grantUserFixture.objectRepository.findById(grantUserFixture.objectId)).get()
                .extracting(OssObject::status).isEqualTo(OssObjectStatus.ACTIVE);
        assertThat(unrelatedFixture.objectRepository.findById(unrelatedFixture.objectId)).get()
                .extracting(OssObject::status).isEqualTo(OssObjectStatus.ACTIVE);
        assertThat(grantUserFixture.objectStore.deletedKey).isNull();
        assertThat(unrelatedFixture.objectStore.deletedKey).isNull();
    }

    @Test
    void missingAndUnauthorizedObjectsShouldHaveTheSameHiddenDeleteError() {
        LifecycleFixture fixture = lifecycleFixture();

        Throwable missing = catchThrowable(() -> fixture.service.deleteObject(
                new DeleteObjectCommand(uuid(99), "unrelated-user")));
        Throwable denied = catchThrowable(() -> fixture.service.deleteObject(
                new DeleteObjectCommand(fixture.objectId, "unrelated-user")));

        assertHiddenObjectNotFound(missing);
        assertHiddenObjectNotFound(denied);
    }

    private static LifecycleFixture lifecycleFixture() {
        UUID objectId = uuid(10);
        UUID versionId = uuid(11);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeVersionRepository versionRepository = new FakeVersionRepository();
        FakeReferenceRepository referenceRepository = new FakeReferenceRepository();
        FakeGrantRepository grantRepository = new FakeGrantRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        OssObjectVersion version = activeVersion(objectId, versionId);
        objectRepository.save(OssObject.stage(
                objectId,
                "USER_AVATAR",
                "community-app",
                "user",
                "USER",
                "owner-7",
                OssVisibility.SIGNED,
                "owner-7",
                CLOCK.instant()
        ).activate(version, CLOCK.instant()));
        versionRepository.save(version);
        return new LifecycleFixture(
                objectId,
                objectRepository,
                grantRepository,
                objectStore,
                new ObjectLifecycleApplicationService(
                        objectRepository, versionRepository, referenceRepository,
                        grantRepository, objectStore, CLOCK)
        );
    }

    private static void assertHiddenObjectNotFound(Throwable throwable) {
        assertThat(throwable).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND);
            assertThat(exception.getMessage()).isEqualTo("OSS object not found");
        });
    }

    private record LifecycleFixture(
            UUID objectId,
            FakeObjectRepository objectRepository,
            FakeGrantRepository grantRepository,
            CapturingObjectStore objectStore,
            ObjectLifecycleApplicationService service
    ) {
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
        private int saveCount;

        @Override
        public void save(OssObject object) {
            saveCount++;
            rows.put(object.objectId(), object);
        }

        @Override
        public Optional<OssObject> findById(UUID objectId) {
            return Optional.ofNullable(rows.get(objectId));
        }
    }

    private static final class FakeVersionRepository implements OssObjectVersionRepository {
        private final Map<UUID, OssObjectVersion> rows = new HashMap<>();
        private int saveCount;

        @Override
        public void save(OssObjectVersion version) {
            saveCount++;
            rows.put(version.versionId(), version);
        }

        @Override
        public Optional<OssObjectVersion> findById(UUID versionId) {
            return Optional.ofNullable(rows.get(versionId));
        }
    }

    private static final class FakeReferenceRepository implements OssObjectReferenceRepository {
        private final Map<UUID, OssObjectReference> rows = new HashMap<>();
        private int saveCount;

        @Override
        public void save(OssObjectReference reference) {
            saveCount++;
            rows.put(reference.referenceId(), reference);
        }

        @Override
        public OssObjectReference insertOrFindExisting(OssObjectReference reference) {
            return rows.computeIfAbsent(reference.referenceId(), ignored -> reference);
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

    private static final class FakeGrantRepository implements OssAccessGrantRepository {
        private final Map<UUID, OssAccessGrant> rows = new HashMap<>();
        private int saveCount;

        @Override
        public void save(OssAccessGrant grant) {
            saveCount++;
            rows.put(grant.grantId(), grant);
        }

        @Override
        public Optional<OssAccessGrant> findById(UUID grantId) {
            return Optional.ofNullable(rows.get(grantId));
        }

        @Override
        public List<OssAccessGrant> findByObjectId(UUID objectId) {
            return rows.values().stream().filter(grant -> objectId.equals(grant.objectId())).toList();
        }
    }

    private static final class CapturingObjectStore implements ObjectStore {
        private String deletedBucket;
        private String deletedKey;

        @Override
        public void put(String bucket, String key, InputStream content, long contentLength, String contentType) {
        }

        @Override
        public Optional<ObjectStoreObject> head(String bucket, String key) {
            return Optional.empty();
        }

        @Override
        public StoredObject get(String bucket, String key) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public void delete(String bucket, String key) {
            deletedBucket = bucket;
            deletedKey = key;
        }

        @Override
        public PresignedObjectUrl presignUpload(String bucket, String key, Duration ttl, String contentType) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public PresignedObjectUrl presignDownload(String bucket, String key, Duration ttl) {
            throw new UnsupportedOperationException("not needed");
        }
    }
}
