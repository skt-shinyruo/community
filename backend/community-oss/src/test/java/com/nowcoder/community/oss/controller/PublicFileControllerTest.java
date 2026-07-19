package com.nowcoder.community.oss.controller;

import com.nowcoder.community.oss.application.ObjectQueryApplicationService;
import com.nowcoder.community.oss.domain.model.OssAccessGrant;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssVisibility;
import com.nowcoder.community.oss.domain.repository.OssAccessGrantRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.infrastructure.config.OssProperties;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStore;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStoreObject;
import com.nowcoder.community.oss.infrastructure.storage.PresignedObjectUrl;
import com.nowcoder.community.oss.infrastructure.storage.StoredObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicFileControllerTest {

    private static final Instant NOW = Instant.parse("2026-05-07T00:00:00Z");
    private static final UUID OBJECT_ID = uuid(1);
    private static final UUID VERSION_ID = uuid(2);
    private static final String FILE_PATH = OBJECT_ID + "/" + VERSION_ID + "/avatar.png";

    @Test
    void publicActiveObjectWithActiveVersionShouldStreamFile() throws Exception {
        Fixture fixture = fixture("PUBLIC_ACTIVE");

        fixture.mvc.perform(get("/files/" + FILE_PATH))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("ETag", "\"etag-1\""))
                .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"))
                .andExpect(content().string("avatar"));

        assertThat(fixture.objectStore.readCount).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0} is not anonymously downloadable")
    @ValueSource(strings = {"PRIVATE", "DELETE_PENDING", "PURGED", "INACTIVE_VERSION"})
    void unavailableObjectOrVersionShouldReturnNotFound(String scenario) throws Exception {
        Fixture fixture = fixture(scenario);

        fixture.mvc.perform(get("/files/" + FILE_PATH))
                .andExpect(status().isNotFound());

        assertThat(fixture.objectStore.readCount).isZero();
    }

    private static Fixture fixture(String scenario) {
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeVersionRepository versionRepository = new FakeVersionRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        OssObjectVersion activeVersion = activeVersion();
        OssVisibility visibility = "PRIVATE".equals(scenario) ? OssVisibility.SIGNED : OssVisibility.PUBLIC;
        OssObject object = OssObject.stage(
                OBJECT_ID,
                "USER_AVATAR",
                "community-app",
                "user",
                "USER",
                "owner-7",
                visibility,
                "owner-7",
                NOW
        ).activate(activeVersion, NOW.plusSeconds(1));
        if ("DELETE_PENDING".equals(scenario)) {
            object = object.deletePending(NOW.plusSeconds(2));
        } else if ("PURGED".equals(scenario)) {
            object = object.purge(NOW.plusSeconds(2));
        }
        objectRepository.save(object);
        versionRepository.save("INACTIVE_VERSION".equals(scenario) ? stagedVersion() : activeVersion);

        OssProperties properties = new OssProperties();
        properties.setPublicBaseUrl("http://localhost:12880");
        ObjectQueryApplicationService queryService = new ObjectQueryApplicationService(
                objectRepository,
                versionRepository,
                new EmptyGrantRepository(),
                objectStore,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PublicFileController(queryService)).build();
        return new Fixture(mvc, objectStore);
    }

    private static OssObjectVersion stagedVersion() {
        return OssObjectVersion.staged(
                VERSION_ID,
                OBJECT_ID,
                "S3_COMPATIBLE",
                "community-oss",
                "objects/1/2/avatar.png",
                "avatar.png",
                "image/png",
                6,
                "sha256-avatar",
                NOW
        );
    }

    private static OssObjectVersion activeVersion() {
        return stagedVersion().withUploadedContent("image/png", 6, "sha256-avatar")
                .activate("etag-1", NOW.plusSeconds(1));
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private record Fixture(MockMvc mvc, CapturingObjectStore objectStore) {
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

    private static final class EmptyGrantRepository implements OssAccessGrantRepository {
        @Override
        public void save(OssAccessGrant grant) {
        }

        @Override
        public Optional<OssAccessGrant> findById(UUID grantId) {
            return Optional.empty();
        }

        @Override
        public List<OssAccessGrant> findByObjectId(UUID objectId) {
            return List.of();
        }
    }

    private static final class CapturingObjectStore implements ObjectStore {
        private int readCount;

        @Override
        public void put(String bucket, String key, InputStream content, long contentLength, String contentType) {
        }

        @Override
        public Optional<ObjectStoreObject> head(String bucket, String key) {
            return Optional.of(new ObjectStoreObject(bucket, key, "image/png", 6, "etag-1", NOW));
        }

        @Override
        public StoredObject get(String bucket, String key) {
            readCount++;
            return new StoredObject(
                    new ByteArrayInputStream("avatar".getBytes(StandardCharsets.UTF_8)),
                    "image/png",
                    6
            );
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
