package com.nowcoder.community.oss.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.oss.application.command.CreateSignedUrlCommand;
import com.nowcoder.community.oss.application.result.ObjectSignedUrlResult;
import com.nowcoder.community.oss.domain.model.OssAccessGrant;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssVisibility;
import com.nowcoder.community.oss.domain.repository.OssAccessGrantRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.domain.service.OssObjectAccessPolicy;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStore;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStoreObject;
import com.nowcoder.community.oss.infrastructure.storage.PresignedObjectUrl;
import com.nowcoder.community.oss.infrastructure.storage.StoredObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ObjectAccessApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-07T00:00:00Z");

    @Test
    void createSignedDownloadUrlShouldUseCurrentVersionAndClampTtl() {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
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
                OssVisibility.SIGNED,
                "7",
                NOW
        ).activate(version, NOW.plusSeconds(1)));
        versionRepository.save(version);
        ObjectAccessApplicationService service = new ObjectAccessApplicationService(
                objectRepository,
                versionRepository,
                grantRepository,
                objectStore,
                clock(),
                new OssObjectAccessPolicy()
        );

        ObjectSignedUrlResult signed = service.createSignedDownloadUrl(new CreateSignedUrlCommand(
                objectId,
                null,
                99_999,
                "7"
        ));

        assertThat(signed.url()).isEqualTo("http://garage.local/community-oss/objects/1/2/avatar.png");
        assertThat(signed.method()).isEqualTo("GET");
        assertThat(signed.cacheControl()).isEqualTo("private, max-age=86400");
        assertThat(objectStore.capturedBucket).isEqualTo("community-oss");
        assertThat(objectStore.capturedKey).isEqualTo("objects/1/2/avatar.png");
        assertThat(objectStore.capturedTtl).isEqualTo(Duration.ofSeconds(86_400));
    }

    @Test
    void createSignedDownloadUrlShouldHideMissingAndUnauthorizedPrivateObjectsBeforeSigning() {
        UUID objectId = uuid(10);
        UUID missingObjectId = uuid(11);
        UUID versionId = uuid(12);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
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
                NOW
        ).activate(version, NOW.plusSeconds(1)));
        versionRepository.save(version);
        ObjectAccessApplicationService service = new ObjectAccessApplicationService(
                objectRepository,
                versionRepository,
                grantRepository,
                objectStore,
                clock(),
                new OssObjectAccessPolicy()
        );

        Throwable missing = catchThrowable(() -> service.createSignedDownloadUrl(
                new CreateSignedUrlCommand(missingObjectId, null, 300, "unrelated-user")));
        Throwable denied = catchThrowable(() -> service.createSignedDownloadUrl(
                new CreateSignedUrlCommand(objectId, null, 300, "unrelated-user")));

        assertHiddenObjectNotFound(missing);
        assertHiddenObjectNotFound(denied);
        assertThat(objectStore.capturedKey).isNull();
    }

    @Test
    void createSignedDownloadUrlShouldAllowValidGrantAndHideExpiredOrRevokedGrants() {
        UUID objectId = uuid(20);
        UUID versionId = uuid(21);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
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
                NOW
        ).activate(version, NOW.plusSeconds(1)));
        versionRepository.save(version);
        grantRepository.save(readGrant(uuid(22), objectId, "grant-user", NOW.plusSeconds(300)));
        grantRepository.save(readGrant(uuid(23), objectId, "expired-user", NOW));
        grantRepository.save(readGrant(uuid(24), objectId, "revoked-user", NOW.plusSeconds(300))
                .revoke(NOW.minusSeconds(1)));
        ObjectAccessApplicationService service = new ObjectAccessApplicationService(
                objectRepository,
                versionRepository,
                grantRepository,
                objectStore,
                clock(),
                new OssObjectAccessPolicy()
        );

        ObjectSignedUrlResult granted = service.createSignedDownloadUrl(
                new CreateSignedUrlCommand(objectId, versionId, 300, "grant-user"));
        Throwable expired = catchThrowable(() -> service.createSignedDownloadUrl(
                new CreateSignedUrlCommand(objectId, versionId, 300, "expired-user")));
        Throwable revoked = catchThrowable(() -> service.createSignedDownloadUrl(
                new CreateSignedUrlCommand(objectId, versionId, 300, "revoked-user")));

        assertThat(granted.method()).isEqualTo("GET");
        assertHiddenObjectNotFound(expired);
        assertHiddenObjectNotFound(revoked);
        assertThat(objectStore.presignCount).isEqualTo(1);
        assertThat(grantRepository.readPrincipals)
                .containsExactly("grant-user", "expired-user", "revoked-user");
    }

    @Test
    void createSignedDownloadUrlShouldHideVersionFromDifferentObject() {
        UUID objectId = uuid(1);
        UUID otherObjectId = uuid(3);
        UUID versionId = uuid(2);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeGrantRepository grantRepository = new FakeGrantRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        objectRepository.save(OssObject.stage(
                objectId,
                "USER_AVATAR",
                "community-app",
                "user",
                "USER",
                "7",
                OssVisibility.SIGNED,
                "7",
                NOW
        ).activate(activeVersion(objectId, versionId), NOW.plusSeconds(1)));
        versionRepository.save(activeVersion(otherObjectId, versionId));
        ObjectAccessApplicationService service = new ObjectAccessApplicationService(
                objectRepository,
                versionRepository,
                grantRepository,
                objectStore,
                clock(),
                new OssObjectAccessPolicy()
        );

        Throwable foreignVersion = catchThrowable(() -> service.createSignedDownloadUrl(new CreateSignedUrlCommand(
                objectId,
                versionId,
                300,
                "7"
        )));

        assertHiddenObjectNotFound(foreignVersion);
        assertThat(objectStore.presignCount).isZero();
    }

    private static OssAccessGrant readGrant(
            UUID grantId,
            UUID objectId,
            String principalValue,
            Instant expiresAt
    ) {
        return OssAccessGrant.readGrant(
                grantId,
                objectId,
                null,
                "USER",
                principalValue,
                "owner-7",
                NOW.minusSeconds(60),
                expiresAt
        );
    }

    private static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static void assertHiddenObjectNotFound(Throwable throwable) {
        assertThat(throwable).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND);
            assertThat(exception.getMessage()).isEqualTo("OSS object not found");
        });
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
                NOW
        ).withUploadedContent("image/png", 6, "sha256-avatar").activate("etag-1", NOW.plusSeconds(1));
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

    private static final class FakeObjectVersionRepository implements OssObjectVersionRepository {
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

    private static final class FakeGrantRepository implements OssAccessGrantRepository {
        private final Map<UUID, OssAccessGrant> rows = new HashMap<>();
        private final List<String> readPrincipals = new ArrayList<>();

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
            return rows.values().stream()
                    .filter(grant -> objectId.equals(grant.objectId()))
                    .toList();
        }

        @Override
        public List<OssAccessGrant> findReadGrants(UUID objectId, UUID versionId, String principalValue) {
            readPrincipals.add(principalValue);
            return OssAccessGrantRepository.super.findReadGrants(objectId, versionId, principalValue);
        }
    }

    private static final class CapturingObjectStore implements ObjectStore {
        private String capturedBucket;
        private String capturedKey;
        private Duration capturedTtl;
        private int presignCount;

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
        }

        @Override
        public PresignedObjectUrl presignUpload(String bucket, String key, Duration ttl, String contentType) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public PresignedObjectUrl presignDownload(String bucket, String key, Duration ttl) {
            presignCount++;
            capturedBucket = bucket;
            capturedKey = key;
            capturedTtl = ttl;
            return new PresignedObjectUrl("http://garage.local/" + bucket + "/" + key, "GET", NOW.plus(ttl), Map.of());
        }
    }
}
