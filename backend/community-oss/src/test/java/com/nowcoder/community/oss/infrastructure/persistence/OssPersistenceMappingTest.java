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

        assertThat(sessionRepository.claimForCompletion(session.sessionId(), NOW.plusSeconds(1))).isTrue();
        OssUploadSession firstClaim = sessionRepository.findById(session.sessionId()).orElseThrow();
        assertThat(firstClaim.claimVersion()).isEqualTo(1L);
        assertThat(sessionRepository.recordCompletionFailure(
                session.sessionId(), 99L, "PUT_FAILED:wrong-claim", NOW.plusSeconds(2))).isFalse();
        assertThat(sessionRepository.recordCompletionFailure(
                session.sessionId(), firstClaim.claimVersion(), "PUT_FAILED:timeout", NOW.plusSeconds(2))).isTrue();
        assertThat(sessionRepository.resetFailedClaim(
                session.sessionId(),
                firstClaim.claimVersion(),
                NOW.plusSeconds(3),
                NOW.plusSeconds(903))).isTrue();
        OssUploadSession reset = sessionRepository.findById(session.sessionId()).orElseThrow();
        assertThat(reset.status().name()).isEqualTo("READY");
        assertThat(reset.claimVersion()).isGreaterThan(firstClaim.claimVersion());
        assertThat(sessionRepository.completeClaim(
                session.sessionId(), firstClaim.claimVersion(), NOW.plusSeconds(4))).isFalse();
        assertThat(sessionRepository.claimForCompletion(session.sessionId(), NOW.plusSeconds(4))).isTrue();
        OssUploadSession retryClaim = sessionRepository.findById(session.sessionId()).orElseThrow();
        assertThat(retryClaim.claimVersion()).isGreaterThan(reset.claimVersion());
        assertThat(sessionRepository.completeClaim(
                session.sessionId(), retryClaim.claimVersion(), NOW.plusSeconds(5))).isTrue();

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

    @Test
    void accessGrantRepositoryShouldUseFocusedReadGrantQuery() {
        UUID objectId = uuid(20);
        UUID versionId = uuid(21);
        FakeGrantMapper mapper = new FakeGrantMapper();
        MyBatisOssAccessGrantRepository repository = new MyBatisOssAccessGrantRepository(mapper);
        OssAccessGrant grant = OssAccessGrant.readGrant(
                uuid(22),
                objectId,
                versionId,
                "USER",
                "reader-7",
                "owner-7",
                NOW,
                NOW.plusSeconds(300)
        );
        repository.save(grant);

        List<OssAccessGrant> candidates = repository.findReadGrants(objectId, versionId, "reader-7");

        assertThat(candidates).containsExactly(grant);
        assertThat(mapper.focusedReadQueryCount).isEqualTo(1);
        assertThat(mapper.focusedObjectId).isEqualTo(objectId);
        assertThat(mapper.focusedVersionId).isEqualTo(versionId);
        assertThat(mapper.focusedPrincipalValue).isEqualTo("reader-7");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static final class FakeObjectMapper implements OssObjectMapper {
        private final Map<UUID, OssObjectDataObject> rows = new HashMap<>();

        @Override
        public int insert(OssObjectDataObject row) {
            return rows.putIfAbsent(row.getObjectId(), row) == null ? 1 : 0;
        }

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
        public int insert(OssObjectVersionDataObject row) {
            return rows.putIfAbsent(row.getVersionId(), row) == null ? 1 : 0;
        }

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
        public int insert(OssUploadSessionDataObject row) {
            return rows.putIfAbsent(row.getSessionId(), row) == null ? 1 : 0;
        }

        @Override
        public int upsert(OssUploadSessionDataObject row) {
            rows.put(row.getSessionId(), row);
            return 1;
        }

        @Override
        public OssUploadSessionDataObject selectById(UUID sessionId) {
            return rows.get(sessionId);
        }

        @Override
        public OssUploadSessionDataObject selectByRequestId(UUID requestId) {
            return rows.values().stream()
                    .filter(row -> requestId.equals(row.getRequestId()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public int claimForCompletion(UUID sessionId, Instant updatedAt) {
            OssUploadSessionDataObject row = rows.get(sessionId);
            if (row == null || !"READY".equals(row.getStatus())) {
                return 0;
            }
            row.setStatus("UPLOADING");
            row.setClaimVersion(row.getClaimVersion() + 1L);
            row.setUpdatedAt(updatedAt);
            row.setLastError("");
            return 1;
        }

        @Override
        public int recordCompletionFailure(
                UUID sessionId,
                long claimVersion,
                String lastError,
                Instant updatedAt
        ) {
            OssUploadSessionDataObject row = rows.get(sessionId);
            if (!matchesClaim(row, claimVersion)) {
                return 0;
            }
            row.setLastError(lastError);
            row.setUpdatedAt(updatedAt);
            return 1;
        }

        @Override
        public int resetFailedClaim(
                UUID sessionId,
                long claimVersion,
                Instant updatedAt,
                Instant retryExpiresAt
        ) {
            OssUploadSessionDataObject row = rows.get(sessionId);
            if (!matchesClaim(row, claimVersion) || !row.getLastError().startsWith("PUT_FAILED:")) {
                return 0;
            }
            row.setStatus("READY");
            row.setClaimVersion(row.getClaimVersion() + 1L);
            row.setLastError("");
            row.setUpdatedAt(updatedAt);
            row.setExpiresAt(retryExpiresAt);
            return 1;
        }

        @Override
        public int completeClaim(UUID sessionId, long claimVersion, Instant completedAt) {
            OssUploadSessionDataObject row = rows.get(sessionId);
            if (!matchesClaim(row, claimVersion)) {
                return 0;
            }
            row.setStatus("COMPLETED");
            row.setCompletedAt(completedAt);
            row.setUpdatedAt(completedAt);
            row.setLastError("");
            return 1;
        }

        @Override
        public int renewReadySession(
                UUID sessionId,
                Instant expectedExpiresAt,
                Instant renewedExpiresAt,
                Instant updatedAt
        ) {
            OssUploadSessionDataObject row = rows.get(sessionId);
            if (row == null
                    || !"READY".equals(row.getStatus())
                    || !expectedExpiresAt.equals(row.getExpiresAt())) {
                return 0;
            }
            row.setExpiresAt(renewedExpiresAt);
            row.setUpdatedAt(updatedAt);
            row.setLastError("");
            return 1;
        }

        @Override
        public List<OssUploadSessionDataObject> listRecoverable(Instant updatedBefore, int limit) {
            return rows.values().stream()
                    .filter(row -> "UPLOADING".equals(row.getStatus()))
                    .filter(row -> !row.getUpdatedAt().isAfter(updatedBefore))
                    .limit(limit)
                    .toList();
        }

        private static boolean matchesClaim(OssUploadSessionDataObject row, long claimVersion) {
            return row != null
                    && "UPLOADING".equals(row.getStatus())
                    && row.getClaimVersion() == claimVersion;
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
        private int focusedReadQueryCount;
        private UUID focusedObjectId;
        private UUID focusedVersionId;
        private String focusedPrincipalValue;

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

        @Override
        public List<OssAccessGrantDataObject> selectReadGrants(
                UUID objectId,
                UUID versionId,
                String principalValue
        ) {
            focusedReadQueryCount++;
            focusedObjectId = objectId;
            focusedVersionId = versionId;
            focusedPrincipalValue = principalValue;
            return rows.values().stream()
                    .filter(row -> objectId.equals(row.getObjectId()))
                    .filter(row -> row.getVersionId() == null || row.getVersionId().equals(versionId))
                    .filter(row -> "USER".equals(row.getPrincipalType()))
                    .filter(row -> principalValue.equals(row.getPrincipalValue()))
                    .filter(row -> "READ".equals(row.getPermission()))
                    .toList();
        }
    }

    private static final class FakeReferenceMapper implements OssObjectReferenceMapper {
        private final Map<UUID, OssObjectReferenceDataObject> rows = new HashMap<>();

        @Override
        public int insert(OssObjectReferenceDataObject row) {
            rows.put(row.getReferenceId(), row);
            return 1;
        }

        @Override
        public int updateLifecycle(OssObjectReferenceDataObject row) {
            if (!rows.containsKey(row.getReferenceId())) {
                return 0;
            }
            rows.put(row.getReferenceId(), row);
            return 1;
        }

        @Override
        public OssObjectReferenceDataObject selectById(UUID referenceId) {
            return rows.get(referenceId);
        }

        @Override
        public OssObjectReferenceDataObject selectByIdForUpdate(UUID referenceId) {
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
