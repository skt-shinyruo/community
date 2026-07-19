package com.nowcoder.community.oss.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.oss.application.command.GrantObjectAccessCommand;
import com.nowcoder.community.oss.application.command.RevokeObjectAccessCommand;
import com.nowcoder.community.oss.application.result.ObjectAccessDecisionResult;
import com.nowcoder.community.oss.domain.model.OssAccessGrant;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssVisibility;
import com.nowcoder.community.oss.domain.repository.OssAccessGrantRepository;
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
import static org.assertj.core.api.Assertions.catchThrowable;

class ObjectPermissionApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-07T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void grantAndRevokeShouldPersistAccessDecisions() {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeGrantRepository grantRepository = new FakeGrantRepository();
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
                "USER",
                "7",
                OssVisibility.PUBLIC,
                "7",
                CLOCK.instant()
        ).activate(version, CLOCK.instant()));
        FakeVersionRepository versionRepository = new FakeVersionRepository();
        versionRepository.save(version);
        ObjectPermissionApplicationService service = new ObjectPermissionApplicationService(
                objectRepository,
                versionRepository,
                grantRepository,
                CLOCK
        );

        ObjectAccessDecisionResult granted = service.grantAccess(new GrantObjectAccessCommand(
                objectId,
                null,
                "USER",
                "7",
                "READ",
                CLOCK.instant().plusSeconds(300),
                "7"
        ));
        ObjectAccessDecisionResult revoked = service.revokeAccess(new RevokeObjectAccessCommand(
                objectId,
                granted.grantId(),
                "7"
        ));

        assertThat(granted.active()).isTrue();
        assertThat(granted.permission()).isEqualTo("READ");
        assertThat(grantRepository.findByObjectId(objectId)).hasSize(1);
        assertThat(revoked.active()).isFalse();
        assertThat(revoked.revokedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void grantAccessShouldHideObjectFromGrantUserAndUnrelatedUser() {
        PermissionFixture fixture = permissionFixture();
        fixture.grantRepository.save(OssAccessGrant.readGrant(
                uuid(20), fixture.objectId, null, "USER", "grant-user", "owner-7",
                CLOCK.instant().minusSeconds(60), CLOCK.instant().plusSeconds(300)));

        Throwable grantUser = catchThrowable(() -> fixture.service.grantAccess(grantCommand(
                fixture.objectId, "grant-user")));
        Throwable unrelated = catchThrowable(() -> fixture.service.grantAccess(grantCommand(
                fixture.objectId, "unrelated-user")));

        assertHiddenObjectNotFound(grantUser);
        assertHiddenObjectNotFound(unrelated);
        assertThat(fixture.grantRepository.findByObjectId(fixture.objectId)).hasSize(1);
    }

    @Test
    void revokeAccessShouldHideObjectFromGrantUserAndUnrelatedUser() {
        PermissionFixture grantUserFixture = permissionFixture();
        OssAccessGrant grantUserGrant = OssAccessGrant.readGrant(
                uuid(21), grantUserFixture.objectId, null, "USER", "grant-user", "owner-7",
                CLOCK.instant().minusSeconds(60), CLOCK.instant().plusSeconds(300));
        grantUserFixture.grantRepository.save(grantUserGrant);
        PermissionFixture unrelatedFixture = permissionFixture();
        OssAccessGrant unrelatedGrant = OssAccessGrant.readGrant(
                uuid(22), unrelatedFixture.objectId, null, "USER", "grant-user", "owner-7",
                CLOCK.instant().minusSeconds(60), CLOCK.instant().plusSeconds(300));
        unrelatedFixture.grantRepository.save(unrelatedGrant);

        Throwable grantUser = catchThrowable(() -> grantUserFixture.service.revokeAccess(
                new RevokeObjectAccessCommand(grantUserFixture.objectId, grantUserGrant.grantId(), "grant-user")));
        Throwable unrelated = catchThrowable(() -> unrelatedFixture.service.revokeAccess(
                new RevokeObjectAccessCommand(unrelatedFixture.objectId, unrelatedGrant.grantId(), "unrelated-user")));

        assertHiddenObjectNotFound(grantUser);
        assertHiddenObjectNotFound(unrelated);
        assertThat(grantUserFixture.grantRepository.findById(grantUserGrant.grantId())).get()
                .extracting(OssAccessGrant::revokedAt).isNull();
        assertThat(unrelatedFixture.grantRepository.findById(unrelatedGrant.grantId())).get()
                .extracting(OssAccessGrant::revokedAt).isNull();
    }

    @Test
    void missingAndUnauthorizedObjectsShouldHaveTheSameHiddenError() {
        PermissionFixture fixture = permissionFixture();

        Throwable missing = catchThrowable(() -> fixture.service.grantAccess(
                grantCommand(uuid(99), "unrelated-user")));
        Throwable denied = catchThrowable(() -> fixture.service.grantAccess(
                grantCommand(fixture.objectId, "unrelated-user")));

        assertHiddenObjectNotFound(missing);
        assertHiddenObjectNotFound(denied);
    }

    @Test
    void grantAccessShouldHideMissingVersion() {
        PermissionFixture fixture = permissionFixture();

        Throwable missingVersion = catchThrowable(() -> fixture.service.grantAccess(
                grantCommand(fixture.objectId, uuid(98), "owner-7")));

        assertHiddenObjectNotFound(missingVersion);
        assertThat(fixture.grantRepository.findByObjectId(fixture.objectId)).isEmpty();
    }

    @Test
    void grantAccessShouldHideVersionFromDifferentObject() {
        PermissionFixture fixture = permissionFixture();
        UUID foreignVersionId = uuid(97);
        fixture.versionRepository.save(activeVersion(uuid(96), foreignVersionId));

        Throwable foreignVersion = catchThrowable(() -> fixture.service.grantAccess(
                grantCommand(fixture.objectId, foreignVersionId, "owner-7")));

        assertHiddenObjectNotFound(foreignVersion);
        assertThat(fixture.grantRepository.findByObjectId(fixture.objectId)).isEmpty();
    }

    @Test
    void revokeAccessShouldHideMissingGrant() {
        PermissionFixture fixture = permissionFixture();

        Throwable missingGrant = catchThrowable(() -> fixture.service.revokeAccess(
                new RevokeObjectAccessCommand(fixture.objectId, uuid(95), "owner-7")));

        assertHiddenObjectNotFound(missingGrant);
    }

    @Test
    void revokeAccessShouldHideGrantFromDifferentObjectWithoutMutation() {
        PermissionFixture fixture = permissionFixture();
        OssAccessGrant foreignGrant = OssAccessGrant.readGrant(
                uuid(94), uuid(93), null, "USER", "reader-8", "foreign-owner",
                CLOCK.instant().minusSeconds(60), CLOCK.instant().plusSeconds(300));
        fixture.grantRepository.save(foreignGrant);

        Throwable foreignRelationship = catchThrowable(() -> fixture.service.revokeAccess(
                new RevokeObjectAccessCommand(fixture.objectId, foreignGrant.grantId(), "owner-7")));

        assertHiddenObjectNotFound(foreignRelationship);
        assertThat(fixture.grantRepository.findById(foreignGrant.grantId())).get()
                .extracting(OssAccessGrant::revokedAt).isNull();
    }

    private static PermissionFixture permissionFixture() {
        UUID objectId = uuid(10);
        UUID versionId = uuid(11);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeVersionRepository versionRepository = new FakeVersionRepository();
        FakeGrantRepository grantRepository = new FakeGrantRepository();
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
        return new PermissionFixture(
                objectId,
                versionRepository,
                grantRepository,
                new ObjectPermissionApplicationService(
                        objectRepository, versionRepository, grantRepository, CLOCK)
        );
    }

    private static GrantObjectAccessCommand grantCommand(UUID objectId, String actorId) {
        return grantCommand(objectId, null, actorId);
    }

    private static GrantObjectAccessCommand grantCommand(UUID objectId, UUID versionId, String actorId) {
        return new GrantObjectAccessCommand(
                objectId,
                versionId,
                "USER",
                "reader-8",
                "READ",
                CLOCK.instant().plusSeconds(300),
                actorId
        );
    }

    private static void assertHiddenObjectNotFound(Throwable throwable) {
        assertThat(throwable).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND);
            assertThat(exception.getMessage()).isEqualTo("OSS object not found");
        });
    }

    private record PermissionFixture(
            UUID objectId,
            FakeVersionRepository versionRepository,
            FakeGrantRepository grantRepository,
            ObjectPermissionApplicationService service
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

        @Override
        public void save(OssObject object) {
            rows.put(object.objectId(), object);
        }

        @Override
        public Optional<OssObject> findById(UUID objectId) {
            return Optional.ofNullable(rows.get(objectId));
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
