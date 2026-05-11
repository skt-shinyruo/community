package com.nowcoder.community.oss.infrastructure.persistence;

import com.nowcoder.community.oss.domain.model.OssAccessGrant;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectReference;
import com.nowcoder.community.oss.domain.model.OssObjectReferenceStatus;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssUploadSession;
import com.nowcoder.community.oss.domain.model.OssUsagePolicy;
import com.nowcoder.community.oss.domain.model.OssVisibility;
import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssAccessGrantDataObject;
import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssObjectDataObject;
import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssObjectReferenceDataObject;
import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssObjectVersionDataObject;
import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssUploadSessionDataObject;
import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssUsagePolicyDataObject;
import com.nowcoder.community.oss.infrastructure.persistence.mapper.OssAccessGrantMapper;
import com.nowcoder.community.oss.infrastructure.persistence.mapper.OssObjectMapper;
import com.nowcoder.community.oss.infrastructure.persistence.mapper.OssObjectReferenceMapper;
import com.nowcoder.community.oss.infrastructure.persistence.mapper.OssObjectVersionMapper;
import com.nowcoder.community.oss.infrastructure.persistence.mapper.OssUploadSessionMapper;
import com.nowcoder.community.oss.infrastructure.persistence.mapper.OssUsagePolicyMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OssPersistenceMappingTest {

    private static final Instant NOW = Instant.parse("2026-05-07T00:00:00Z");

    @Test
    void repositoriesShouldPersistAndRestoreCoreDomainModels() {
        FakeObjectMapper objectMapper = new FakeObjectMapper();
        FakeVersionMapper versionMapper = new FakeVersionMapper();
        FakeSessionMapper sessionMapper = new FakeSessionMapper();
        FakePolicyMapper policyMapper = new FakePolicyMapper();
        FakeGrantMapper grantMapper = new FakeGrantMapper();
        FakeReferenceMapper referenceMapper = new FakeReferenceMapper();
        MyBatisOssObjectRepository objectRepository = new MyBatisOssObjectRepository(objectMapper);
        MyBatisOssObjectVersionRepository versionRepository = new MyBatisOssObjectVersionRepository(versionMapper);
        MyBatisOssUploadSessionRepository sessionRepository = new MyBatisOssUploadSessionRepository(sessionMapper);
        MyBatisOssUsagePolicyRepository policyRepository = new MyBatisOssUsagePolicyRepository(policyMapper);
        MyBatisOssAccessGrantRepository grantRepository = new MyBatisOssAccessGrantRepository(grantMapper);
        MyBatisOssObjectReferenceRepository referenceRepository = new MyBatisOssObjectReferenceRepository(referenceMapper);

        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
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
                "objects/" + objectId + "/" + versionId + "/avatar.png",
                "avatar.png",
                "image/png",
                6,
                "sha256-avatar",
                NOW
        );
        OssUploadSession session = OssUploadSession.ready(
                uuid(3),
                objectId,
                versionId,
                "PROXY",
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
        OssUsagePolicy policy = new OssUsagePolicy(
                "USER_AVATAR",
                OssVisibility.PUBLIC,
                2_097_152,
                Set.of("image/png", "image/jpeg"),
                false,
                false,
                true,
                300,
                900,
                "public, max-age=31536000, immutable",
                "no-store",
                0,
                7
        );
        OssAccessGrant grant = OssAccessGrant.readGrant(
                uuid(4),
                objectId,
                versionId,
                "USER",
                "7",
                "7",
                NOW,
                NOW.plusSeconds(3600)
        );
        OssObjectReference reference = OssObjectReference.active(
                uuid(5),
                objectId,
                versionId,
                "community-app",
                "user",
                "avatar",
                "7",
                "PRIMARY",
                NOW,
                null
        );

        objectRepository.save(object.activate(version.activate("etag-1", NOW), NOW));
        versionRepository.save(version);
        sessionRepository.save(session);
        policyRepository.save(policy);
        grantRepository.save(grant);
        referenceRepository.save(reference);

        assertThat(objectRepository.findById(objectId)).get().extracting(OssObject::currentVersionId).isEqualTo(versionId);
        assertThat(versionRepository.findById(versionId)).get().extracting(OssObjectVersion::storageKey).isEqualTo(version.storageKey());
        assertThat(sessionRepository.findById(session.sessionId())).get().extracting(OssUploadSession::expectedFileName).isEqualTo(session.expectedFileName());
        assertThat(policyRepository.findByUsage("USER_AVATAR")).get().extracting(OssUsagePolicy::maxBytes).isEqualTo(2_097_152L);
        assertThat(grantRepository.findById(grant.grantId())).get().extracting(OssAccessGrant::principalValue).isEqualTo("7");
        assertThat(grantRepository.findByObjectId(objectId)).hasSize(1);
        assertThat(referenceRepository.findById(reference.referenceId())).get().extracting(OssObjectReference::status)
                .isEqualTo(OssObjectReferenceStatus.ACTIVE);
        assertThat(referenceRepository.findByObjectId(objectId)).hasSize(1);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static final class FakeObjectMapper implements OssObjectMapper {
        private final Map<UUID, OssObjectDataObject> rows = new HashMap<>();

        @Override
        public int upsert(OssObjectDataObject row) {
            rows.put(row.getObjectId(), row);
            return 1;
        }

        @Override
        public OssObjectDataObject selectById(UUID objectId) {
            return rows.get(objectId);
        }
    }

    private static final class FakeVersionMapper implements OssObjectVersionMapper {
        private final Map<UUID, OssObjectVersionDataObject> rows = new HashMap<>();

        @Override
        public int upsert(OssObjectVersionDataObject row) {
            rows.put(row.getVersionId(), row);
            return 1;
        }

        @Override
        public OssObjectVersionDataObject selectById(UUID versionId) {
            return rows.get(versionId);
        }
    }

    private static final class FakeSessionMapper implements OssUploadSessionMapper {
        private final Map<UUID, OssUploadSessionDataObject> rows = new HashMap<>();

        @Override
        public int upsert(OssUploadSessionDataObject row) {
            rows.put(row.getSessionId(), row);
            return 1;
        }

        @Override
        public OssUploadSessionDataObject selectById(UUID sessionId) {
            return rows.get(sessionId);
        }
    }

    private static final class FakePolicyMapper implements OssUsagePolicyMapper {
        private final Map<String, OssUsagePolicyDataObject> rows = new HashMap<>();

        @Override
        public int upsert(OssUsagePolicyDataObject row) {
            rows.put(row.getUsage(), row);
            return 1;
        }

        @Override
        public OssUsagePolicyDataObject selectByUsage(String usage) {
            return rows.get(usage);
        }
    }

    private static final class FakeGrantMapper implements OssAccessGrantMapper {
        private final Map<UUID, OssAccessGrantDataObject> rows = new HashMap<>();

        @Override
        public int upsert(OssAccessGrantDataObject row) {
            rows.put(row.getGrantId(), row);
            return 1;
        }

        @Override
        public OssAccessGrantDataObject selectById(UUID grantId) {
            return rows.get(grantId);
        }

        @Override
        public List<OssAccessGrantDataObject> selectByObjectId(UUID objectId) {
            return rows.values().stream()
                    .filter(row -> objectId.equals(row.getObjectId()))
                    .toList();
        }
    }

    private static final class FakeReferenceMapper implements OssObjectReferenceMapper {
        private final Map<UUID, OssObjectReferenceDataObject> rows = new HashMap<>();

        @Override
        public int upsert(OssObjectReferenceDataObject row) {
            rows.put(row.getReferenceId(), row);
            return 1;
        }

        @Override
        public OssObjectReferenceDataObject selectById(UUID referenceId) {
            return rows.get(referenceId);
        }

        @Override
        public List<OssObjectReferenceDataObject> selectByObjectId(UUID objectId) {
            return rows.values().stream()
                    .filter(row -> objectId.equals(row.getObjectId()))
                    .toList();
        }
    }
}
