package com.nowcoder.community.oss.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.oss.application.result.ObjectDownloadResult;
import com.nowcoder.community.oss.application.result.ObjectMetadataResult;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssAccessGrant;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssVisibility;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.domain.repository.OssAccessGrantRepository;
import com.nowcoder.community.oss.infrastructure.config.OssProperties;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStore;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStoreObject;
import com.nowcoder.community.oss.infrastructure.storage.PresignedObjectUrl;
import com.nowcoder.community.oss.infrastructure.storage.StoredObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

class ObjectQueryApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-07T00:00:00Z");

    @Test
    void getInternalMetadataShouldHideForeignOwnerWithoutUserGrantOrStorageEffects() {
        UUID objectId = uuid(80);
        UUID versionId = uuid(81);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeGrantRepository grantRepository = new FakeGrantRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        OssObjectVersion version = activeVersion(objectId, versionId);
        objectRepository.save(OssObject.stage(
                objectId,
                "DRIVE_FILE",
                "community-app",
                "drive",
                "DRIVE_UPLOAD",
                "upload-7",
                OssVisibility.SIGNED,
                "user-7",
                NOW
        ).activate(version, NOW.plusSeconds(1)));
        versionRepository.save(version);
        ObjectQueryApplicationService service = new ObjectQueryApplicationService(
                objectRepository,
                versionRepository,
                grantRepository,
                objectStore,
                properties("http://localhost:12880/"),
                clock()
        );

        Throwable failure = catchThrowable(() -> service.getInternalMetadata(objectId, "profile-service"));

        assertAll(
                () -> assertHiddenObjectNotFound(failure),
                () -> assertThat(grantRepository.readPrincipals).isEmpty(),
                () -> assertThat(objectStore.capturedBucket).isNull(),
                () -> assertThat(objectStore.capturedKey).isNull()
        );
    }

    @Test
    void getInternalMetadataShouldReturnNonUserObjectToOwningServiceWithoutUserGrantLookup() {
        UUID objectId = uuid(82);
        UUID versionId = uuid(83);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeGrantRepository grantRepository = new FakeGrantRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        OssObjectVersion version = activeVersion(objectId, versionId);
        objectRepository.save(OssObject.stage(
                objectId,
                "DRIVE_FILE",
                "community-app",
                "drive",
                "DRIVE_UPLOAD",
                "upload-7",
                OssVisibility.SIGNED,
                "user-7",
                NOW
        ).activate(version, NOW.plusSeconds(1)));
        versionRepository.save(version);
        ObjectQueryApplicationService service = new ObjectQueryApplicationService(
                objectRepository,
                versionRepository,
                grantRepository,
                objectStore,
                properties("http://localhost:12880/"),
                clock()
        );
        ObjectMetadataResult[] metadata = new ObjectMetadataResult[1];

        Throwable failure = catchThrowable(() ->
                metadata[0] = service.getInternalMetadata(objectId, "community-app"));

        assertAll(
                () -> assertThat(failure).isNull(),
                () -> assertThat(metadata[0]).isNotNull(),
                () -> assertThat(metadata[0].objectId()).isEqualTo(objectId),
                () -> assertThat(grantRepository.readPrincipals).isEmpty(),
                () -> assertThat(objectStore.capturedBucket).isNull(),
                () -> assertThat(objectStore.capturedKey).isNull()
        );
    }

    @Test
    void getInternalMetadataShouldHideUserOwnedObjectFromServiceSubject() {
        UUID objectId = uuid(84);
        UUID versionId = uuid(85);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeGrantRepository grantRepository = new FakeGrantRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        OssObjectVersion version = activeVersion(objectId, versionId);
        objectRepository.save(privateObject(objectId, version));
        versionRepository.save(version);
        ObjectQueryApplicationService service = new ObjectQueryApplicationService(
                objectRepository,
                versionRepository,
                grantRepository,
                objectStore,
                properties("http://localhost:12880/"),
                clock()
        );

        Throwable failure = catchThrowable(() -> service.getInternalMetadata(objectId, "community-app"));

        assertAll(
                () -> assertHiddenObjectNotFound(failure),
                () -> assertThat(grantRepository.readPrincipals).isEmpty(),
                () -> assertThat(objectStore.capturedBucket).isNull(),
                () -> assertThat(objectStore.capturedKey).isNull()
        );
    }

    @Test
    void resolvePublicFileShouldUseCanonicalPathOnly() throws Exception {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        OssObjectVersion version = activeVersion(objectId, versionId);
        OssObject object = OssObject.stage(
                objectId,
                "USER_AVATAR",
                "community-app",
                "user",
                "USER",
                "7",
                OssVisibility.PUBLIC,
                "7",
                NOW
        ).activate(version, NOW.plusSeconds(1));
        objectRepository.save(object);
        versionRepository.save(version);
        ObjectQueryApplicationService service = new ObjectQueryApplicationService(
                objectRepository,
                versionRepository,
                new FakeGrantRepository(),
                objectStore,
                properties("http://localhost:12880/"),
                clock()
        );

        ObjectMetadataResult metadata = service.getMetadata(objectId, "7");
        ObjectDownloadResult download = service.resolvePublicFile(objectId + "/" + versionId + "/avatar.png");

        assertThat(metadata.publicUrl()).isEqualTo(
                "http://localhost:12880/files/" + objectId + "/" + versionId + "/avatar.png"
        );
        assertThat(download.content().readAllBytes()).isEqualTo("avatar".getBytes(StandardCharsets.UTF_8));
        assertThat(download.contentType()).isEqualTo("image/png");
        assertThat(download.contentLength()).isEqualTo(6);
        assertThat(download.etag()).isEqualTo("etag-1");
        assertThat(download.cacheControl()).isEqualTo("public, max-age=31536000, immutable");
        assertThat(download.fileName()).isEqualTo("avatar.png");
        assertThat(objectStore.capturedBucket).isEqualTo("community-oss");
        assertThat(objectStore.capturedKey).isEqualTo("objects/1/2/avatar.png");
    }

    @Test
    void getMetadataShouldAllowOwnerAndValidReadGrant() {
        UUID objectId = uuid(10);
        UUID versionId = uuid(12);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeGrantRepository grantRepository = new FakeGrantRepository();
        OssObjectVersion version = activeVersion(objectId, versionId);
        objectRepository.save(privateObject(objectId, version));
        versionRepository.save(version);
        grantRepository.save(readGrant(uuid(13), objectId, "grant-user", NOW.plusSeconds(300)));
        ObjectQueryApplicationService service = new ObjectQueryApplicationService(
                objectRepository,
                versionRepository,
                grantRepository,
                new CapturingObjectStore(),
                properties("http://localhost:12880/"),
                clock()
        );

        ObjectMetadataResult ownerMetadata = service.getMetadata(objectId, "owner-7");
        ObjectMetadataResult grantMetadata = service.getMetadata(objectId, "grant-user");

        assertThat(ownerMetadata.objectId()).isEqualTo(objectId);
        assertThat(grantMetadata.objectId()).isEqualTo(objectId);
        assertThat(grantRepository.readPrincipals).containsExactly("owner-7", "grant-user");
    }

    @Test
    void getMetadataShouldHideMissingAndUnauthorizedPrivateObjects() {
        UUID objectId = uuid(10);
        UUID missingObjectId = uuid(11);
        UUID versionId = uuid(12);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        OssObjectVersion version = activeVersion(objectId, versionId);
        objectRepository.save(privateObject(objectId, version));
        versionRepository.save(version);
        FakeGrantRepository grantRepository = new FakeGrantRepository();
        grantRepository.save(readGrant(uuid(13), objectId, "expired-user", NOW));
        grantRepository.save(readGrant(uuid(14), objectId, "revoked-user", NOW.plusSeconds(300))
                .revoke(NOW.minusSeconds(1)));
        ObjectQueryApplicationService service = new ObjectQueryApplicationService(
                objectRepository,
                versionRepository,
                grantRepository,
                new CapturingObjectStore(),
                properties("http://localhost:12880/"),
                clock()
        );

        Throwable missing = catchThrowable(() -> service.getMetadata(missingObjectId, "unrelated-user"));
        Throwable denied = catchThrowable(() -> service.getMetadata(objectId, "unrelated-user"));
        Throwable expired = catchThrowable(() -> service.getMetadata(objectId, "expired-user"));
        Throwable revoked = catchThrowable(() -> service.getMetadata(objectId, "revoked-user"));

        assertHiddenObjectNotFound(missing);
        assertHiddenObjectNotFound(denied);
        assertHiddenObjectNotFound(expired);
        assertHiddenObjectNotFound(revoked);
    }

    @Test
    void getMetadataShouldHideCrossObjectCurrentVersionFromOwnerAndGrantReader() {
        UUID objectId = uuid(20);
        UUID versionId = uuid(21);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeGrantRepository grantRepository = new FakeGrantRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        OssObjectVersion canonicalVersion = activeVersion(objectId, versionId);
        objectRepository.save(privateObject(objectId, canonicalVersion));
        versionRepository.save(activeVersion(uuid(22), versionId));
        grantRepository.save(readGrant(uuid(23), objectId, "grant-user", NOW.plusSeconds(300)));
        ObjectQueryApplicationService service = new ObjectQueryApplicationService(
                objectRepository,
                versionRepository,
                grantRepository,
                objectStore,
                properties("http://localhost:12880/"),
                clock()
        );

        Throwable owner = catchThrowable(() -> service.getMetadata(objectId, "owner-7"));
        Throwable grantReader = catchThrowable(() -> service.getMetadata(objectId, "grant-user"));

        assertHiddenObjectNotFound(owner);
        assertHiddenObjectNotFound(grantReader);
        assertThat(objectStore.capturedBucket).isNull();
        assertThat(objectStore.capturedKey).isNull();
    }

    @Test
    void resolvePublicFileShouldRejectNonPublicCanonicalPath() {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        OssObjectVersion version = activeVersion(objectId, versionId);
        objectRepository.save(OssObject.stage(
                objectId,
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                "7",
                OssVisibility.SIGNED,
                "7",
                NOW
        ).activate(version, NOW.plusSeconds(1)));
        versionRepository.save(version);
        ObjectQueryApplicationService service = new ObjectQueryApplicationService(
                objectRepository,
                versionRepository,
                new FakeGrantRepository(),
                objectStore,
                properties("http://localhost:12880/"),
                clock()
        );

        ObjectDownloadResult download = service.resolvePublicFile(objectId + "/" + versionId + "/avatar.png");

        assertThat(download).isNull();
        assertThat(objectStore.capturedKey).isNull();
    }

    @Test
    void resolvePublicFileShouldRejectUnavailableCanonicalObject() {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        OssObjectVersion version = activeVersion(objectId, versionId);
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
        ).activate(version, NOW.plusSeconds(1)).deletePending(NOW.plusSeconds(2));
        objectRepository.save(object);
        versionRepository.save(version);
        ObjectQueryApplicationService service = new ObjectQueryApplicationService(
                objectRepository,
                versionRepository,
                new FakeGrantRepository(),
                objectStore,
                properties("http://localhost:12880/"),
                clock()
        );

        ObjectDownloadResult download = service.resolvePublicFile(objectId + "/" + versionId + "/avatar.png");

        assertThat(download).isNull();
        assertThat(objectStore.capturedKey).isNull();
    }

    @Test
    void resolvePublicFileShouldRejectNonCanonicalAliasPath() {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
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
                NOW
        ).activate(version, NOW.plusSeconds(1)));
        versionRepository.save(version);
        ObjectQueryApplicationService service = new ObjectQueryApplicationService(
                objectRepository,
                versionRepository,
                new FakeGrantRepository(),
                objectStore,
                properties("http://localhost:12880/"),
                clock()
        );

        ObjectDownloadResult download = service.resolvePublicFile("avatar/7/0123456789abcdef0123456789abcdef");

        assertThat(download).isNull();
        assertThat(objectStore.capturedKey).isNull();
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

    private static OssObject privateObject(UUID objectId, OssObjectVersion version) {
        return OssObject.stage(
                objectId,
                "USER_AVATAR",
                "community-app",
                "user",
                "USER",
                "owner-7",
                OssVisibility.SIGNED,
                "owner-7",
                NOW
        ).activate(version, NOW.plusSeconds(1));
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

    private static OssProperties properties(String publicBaseUrl) {
        OssProperties properties = new OssProperties();
        properties.setPublicBaseUrl(publicBaseUrl);
        return properties;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
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

        @Override
        public void put(String bucket, String key, InputStream content, long contentLength, String contentType) {
        }

        @Override
        public Optional<ObjectStoreObject> head(String bucket, String key) {
            capturedBucket = bucket;
            capturedKey = key;
            return Optional.of(new ObjectStoreObject(bucket, key, "image/png", 6, "etag-1", NOW.plusSeconds(2)));
        }

        @Override
        public StoredObject get(String bucket, String key) {
            capturedBucket = bucket;
            capturedKey = key;
            return new StoredObject(new ByteArrayInputStream("avatar".getBytes(StandardCharsets.UTF_8)), "image/png", 6);
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
            throw new UnsupportedOperationException("not needed");
        }
    }
}
