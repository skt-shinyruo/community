package com.nowcoder.community.oss.application;

import com.nowcoder.community.oss.application.result.ObjectDownloadResult;
import com.nowcoder.community.oss.application.result.ObjectMetadataResult;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectAlias;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssVisibility;
import com.nowcoder.community.oss.domain.repository.OssObjectAliasRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectQueryApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-07T00:00:00Z");

    @Test
    void resolvePublicFileShouldSupportCanonicalAndLegacyAliasPaths() throws Exception {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        String aliasKey = "avatar/7/0123456789abcdef0123456789abcdef";
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeAliasRepository aliasRepository = new FakeAliasRepository();
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
        ).activate(version, NOW.plusSeconds(1));
        objectRepository.save(object);
        versionRepository.save(version);
        aliasRepository.save(OssObjectAlias.active(aliasKey, objectId, versionId, NOW));
        ObjectQueryApplicationService service = new ObjectQueryApplicationService(
                objectRepository,
                versionRepository,
                aliasRepository,
                objectStore,
                properties("http://localhost:12880/")
        );

        ObjectMetadataResult metadata = service.getMetadata(objectId);
        ObjectDownloadResult download = service.resolvePublicFile(aliasKey);

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
                new FakeAliasRepository(),
                objectStore,
                properties("http://localhost:12880/")
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
                new FakeAliasRepository(),
                objectStore,
                properties("http://localhost:12880/")
        );

        ObjectDownloadResult download = service.resolvePublicFile(objectId + "/" + versionId + "/avatar.png");

        assertThat(download).isNull();
        assertThat(objectStore.capturedKey).isNull();
    }

    @Test
    void resolvePublicFileShouldRejectExpiredAlias() {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeAliasRepository aliasRepository = new FakeAliasRepository();
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
        aliasRepository.save(new OssObjectAlias(
                "avatar/7/0123456789abcdef0123456789abcdef",
                objectId,
                versionId,
                "ACTIVE",
                NOW.minusSeconds(1),
                NOW
        ));
        ObjectQueryApplicationService service = new ObjectQueryApplicationService(
                objectRepository,
                versionRepository,
                aliasRepository,
                objectStore,
                properties("http://localhost:12880/")
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

    private static OssProperties properties(String publicBaseUrl) {
        OssProperties properties = new OssProperties();
        properties.setPublicBaseUrl(publicBaseUrl);
        return properties;
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

    private static final class FakeAliasRepository implements OssObjectAliasRepository {
        private final Map<String, OssObjectAlias> rows = new HashMap<>();

        @Override
        public void save(OssObjectAlias alias) {
            rows.put(alias.aliasKey(), alias);
        }

        @Override
        public Optional<OssObjectAlias> findByAliasKey(String aliasKey) {
            return Optional.ofNullable(rows.get(aliasKey));
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
