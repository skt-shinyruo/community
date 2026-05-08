package com.nowcoder.community.oss.application;

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
                "avatar",
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
                "avatar",
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
                "avatar",
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

    private static final class FakeGrantRepository implements OssAccessGrantRepository {
        private final Map<UUID, OssAccessGrant> rows = new HashMap<>();

        @Override
        public void save(OssAccessGrant grant) {
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
