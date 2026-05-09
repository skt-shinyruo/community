package com.nowcoder.community.oss.application;

import com.nowcoder.community.oss.application.command.CompleteObjectUploadCommand;
import com.nowcoder.community.oss.application.command.ObjectUploadContent;
import com.nowcoder.community.oss.application.command.PrepareObjectUploadCommand;
import com.nowcoder.community.oss.application.result.ObjectMetadataResult;
import com.nowcoder.community.oss.application.result.ObjectUploadSessionResult;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectAlias;
import com.nowcoder.community.oss.domain.model.OssObjectStatus;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssUploadSession;
import com.nowcoder.community.oss.domain.model.OssUploadSessionStatus;
import com.nowcoder.community.oss.domain.model.OssUsagePolicy;
import com.nowcoder.community.oss.domain.model.OssVisibility;
import com.nowcoder.community.oss.domain.repository.OssObjectAliasRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.domain.repository.OssUploadSessionRepository;
import com.nowcoder.community.oss.domain.repository.OssUsagePolicyRepository;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStore;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStoreObject;
import com.nowcoder.community.oss.infrastructure.storage.PresignedObjectUrl;
import com.nowcoder.community.oss.infrastructure.storage.StoredObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectUploadApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-07T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void prepareAndCompleteProxyUploadShouldActivateVersionAndCreateLegacyAlias() {
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeUploadSessionRepository uploadSessionRepository = new FakeUploadSessionRepository();
        FakeAliasRepository aliasRepository = new FakeAliasRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        ObjectUploadApplicationService service = new ObjectUploadApplicationService(
                objectRepository,
                versionRepository,
                uploadSessionRepository,
                aliasRepository,
                objectStore,
                "community-oss",
                "http://localhost:12880",
                CLOCK
        );
        UUID ownerId = uuid(7);
        String legacyAlias = "avatar/" + ownerId + "/0123456789abcdef0123456789abcdef";

        ObjectUploadSessionResult prepared = service.prepareUpload(new PrepareObjectUploadCommand(
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                ownerId.toString(),
                "PUBLIC",
                "avatar.png",
                "image/png",
                6,
                "sha256-avatar",
                legacyAlias,
                ownerId.toString()
        ));

        assertThat(prepared.uploadMode()).isEqualTo("PROXY");
        assertThat(prepared.uploadUrl()).isEqualTo("/api/oss/objects/" + prepared.objectId() + "/complete");

        ObjectMetadataResult completed = service.completeUpload(new CompleteObjectUploadCommand(
                prepared.sessionId(),
                prepared.objectId(),
                prepared.versionId(),
                new ObjectUploadContent(
                        () -> new ByteArrayInputStream("avatar".getBytes(StandardCharsets.UTF_8)),
                        "image/png",
                        6,
                        "sha256-avatar"
                )
        ));

        assertThat(completed.status()).isEqualTo(OssObjectStatus.ACTIVE.name());
        assertThat(completed.currentVersionId()).isEqualTo(prepared.versionId());
        assertThat(completed.publicUrl()).isEqualTo(
                "http://localhost:12880/files/" + prepared.objectId() + "/" + prepared.versionId() + "/avatar.png"
        );
        assertThat(objectStore.capturedKey).isEqualTo(
                "objects/" + prepared.objectId() + "/" + prepared.versionId() + "/avatar.png"
        );
        assertThat(uploadSessionRepository.findById(prepared.sessionId()).orElseThrow().status())
                .isEqualTo(OssUploadSessionStatus.COMPLETED);
        assertThat(aliasRepository.findByAliasKey(legacyAlias).orElseThrow().objectId())
                .isEqualTo(prepared.objectId());
    }

    @Test
    void prepareUploadShouldApplyUsagePolicyDefaultsAndUploadTtl() {
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeUploadSessionRepository uploadSessionRepository = new FakeUploadSessionRepository();
        FakeUsagePolicyRepository policyRepository = new FakeUsagePolicyRepository();
        policyRepository.save(new OssUsagePolicy(
                "USER_AVATAR",
                OssVisibility.SIGNED,
                5,
                Set.of("image/png"),
                true,
                false,
                true,
                120,
                60,
                "",
                "",
                0,
                0
        ));
        ObjectUploadApplicationService service = new ObjectUploadApplicationService(
                objectRepository,
                versionRepository,
                uploadSessionRepository,
                new FakeAliasRepository(),
                policyRepository,
                new CapturingObjectStore(),
                "community-oss",
                "http://localhost:12880",
                CLOCK
        );

        ObjectUploadSessionResult prepared = service.prepareUpload(new PrepareObjectUploadCommand(
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                "7",
                "",
                "avatar.png",
                "image/png",
                5,
                "sha256-avatar",
                "",
                "7"
        ));

        assertThat(objectRepository.findById(prepared.objectId()).orElseThrow().visibility())
                .isEqualTo(OssVisibility.SIGNED);
        assertThat(uploadSessionRepository.findById(prepared.sessionId()).orElseThrow().expiresAt())
                .isEqualTo(CLOCK.instant().plusSeconds(60));
    }

    @Test
    void prepareUploadShouldRejectContentThatViolatesUsagePolicy() {
        FakeUsagePolicyRepository policyRepository = new FakeUsagePolicyRepository();
        policyRepository.save(new OssUsagePolicy(
                "USER_AVATAR",
                OssVisibility.PUBLIC,
                5,
                Set.of("image/png"),
                false,
                false,
                true,
                120,
                60,
                "",
                "",
                0,
                0
        ));
        ObjectUploadApplicationService service = new ObjectUploadApplicationService(
                new FakeObjectRepository(),
                new FakeObjectVersionRepository(),
                new FakeUploadSessionRepository(),
                new FakeAliasRepository(),
                policyRepository,
                new CapturingObjectStore(),
                "community-oss",
                "http://localhost:12880",
                CLOCK
        );

        assertThatThrownBy(() -> service.prepareUpload(new PrepareObjectUploadCommand(
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                "7",
                "PUBLIC",
                "avatar.png",
                "image/png",
                6,
                "",
                "",
                "7"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("upload exceeds usage policy maxBytes");
    }

    @Test
    void completeUploadShouldRejectActualContentThatViolatesUsagePolicy() {
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeUploadSessionRepository uploadSessionRepository = new FakeUploadSessionRepository();
        FakeUsagePolicyRepository policyRepository = new FakeUsagePolicyRepository();
        policyRepository.save(new OssUsagePolicy(
                "USER_AVATAR",
                OssVisibility.PUBLIC,
                5,
                Set.of("image/png"),
                false,
                false,
                true,
                120,
                60,
                "",
                "",
                0,
                0
        ));
        CapturingObjectStore objectStore = new CapturingObjectStore();
        ObjectUploadApplicationService service = new ObjectUploadApplicationService(
                objectRepository,
                versionRepository,
                uploadSessionRepository,
                new FakeAliasRepository(),
                policyRepository,
                objectStore,
                "community-oss",
                "http://localhost:12880",
                CLOCK
        );
        ObjectUploadSessionResult prepared = service.prepareUpload(new PrepareObjectUploadCommand(
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                "7",
                "PUBLIC",
                "avatar.png",
                "image/png",
                0,
                "",
                "",
                "7"
        ));

        assertThatThrownBy(() -> service.completeUpload(new CompleteObjectUploadCommand(
                prepared.sessionId(),
                prepared.objectId(),
                prepared.versionId(),
                new ObjectUploadContent(
                        () -> new ByteArrayInputStream("avatar".getBytes(StandardCharsets.UTF_8)),
                        "image/png",
                        6,
                        ""
                )
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("upload exceeds usage policy maxBytes");
        assertThat(objectStore.capturedKey).isNull();
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

    private static final class FakeUploadSessionRepository implements OssUploadSessionRepository {
        private final Map<UUID, OssUploadSession> rows = new HashMap<>();

        @Override
        public void save(OssUploadSession session) {
            rows.put(session.sessionId(), session);
        }

        @Override
        public Optional<OssUploadSession> findById(UUID sessionId) {
            return Optional.ofNullable(rows.get(sessionId));
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

    private static final class FakeUsagePolicyRepository implements OssUsagePolicyRepository {
        private final Map<String, OssUsagePolicy> rows = new HashMap<>();

        @Override
        public void save(OssUsagePolicy policy) {
            rows.put(policy.usage(), policy);
        }

        @Override
        public Optional<OssUsagePolicy> findByUsage(String usage) {
            return Optional.ofNullable(rows.get(usage));
        }
    }

    private static final class CapturingObjectStore implements ObjectStore {
        private String capturedBucket;
        private String capturedKey;

        @Override
        public void put(String bucket, String key, InputStream content, long contentLength, String contentType) {
            capturedBucket = bucket;
            capturedKey = key;
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
            throw new UnsupportedOperationException("not needed");
        }
    }
}
