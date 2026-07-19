package com.nowcoder.community.oss.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.ErrorKind;
import com.nowcoder.community.oss.application.command.BindObjectReferenceCommand;
import com.nowcoder.community.oss.application.command.ReleaseObjectReferenceCommand;
import com.nowcoder.community.oss.application.result.ObjectReferenceResult;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectReference;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssVisibility;
import com.nowcoder.community.oss.domain.repository.OssObjectReferenceRepository;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

class ObjectReferenceApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-07T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void bindInternalReferenceShouldHideForeignServiceWithoutInsertion() {
        Fixture fixture = fixture();

        Throwable failure = catchThrowable(() -> fixture.serviceAt(CLOCK.instant()).bindInternalReference(
                "profile-service",
                deterministicCommand(
                        uuid(80),
                        fixture.objectId(),
                        fixture.versionId(),
                        "upload-7",
                        "PRIMARY",
                        null,
                        "user-7")));

        assertAll(
                () -> assertHiddenObjectNotFound(failure),
                () -> assertThat(fixture.referenceRepository.saveCount()).isZero(),
                () -> assertThat(fixture.referenceRepository.findByObjectId(fixture.objectId())).isEmpty()
        );
    }

    @Test
    void bindInternalReferenceShouldHideMissingVersionWithoutInsertion() {
        Fixture fixture = fixture();

        Throwable failure = catchThrowable(() -> fixture.serviceAt(CLOCK.instant()).bindInternalReference(
                "community-app",
                deterministicCommand(
                        uuid(82),
                        fixture.objectId(),
                        uuid(83),
                        "upload-7",
                        "PRIMARY",
                        null,
                        "user-7")));

        assertAll(
                () -> assertHiddenObjectNotFound(failure),
                () -> assertThat(fixture.referenceRepository.saveCount()).isZero(),
                () -> assertThat(fixture.referenceRepository.findByObjectId(fixture.objectId())).isEmpty()
        );
    }

    @Test
    void bindInternalReferenceShouldHideCrossObjectVersionWithoutInsertion() {
        Fixture fixture = fixture();
        UUID foreignObjectId = uuid(84);
        UUID foreignVersionId = uuid(85);
        fixture.versionRepository.rows.put(foreignVersionId, activeVersion(foreignObjectId, foreignVersionId));

        Throwable failure = catchThrowable(() -> fixture.serviceAt(CLOCK.instant()).bindInternalReference(
                "community-app",
                deterministicCommand(
                        uuid(86),
                        fixture.objectId(),
                        foreignVersionId,
                        "upload-7",
                        "PRIMARY",
                        null,
                        "user-7")));

        assertAll(
                () -> assertHiddenObjectNotFound(failure),
                () -> assertThat(fixture.referenceRepository.saveCount()).isZero(),
                () -> assertThat(fixture.referenceRepository.findByObjectId(fixture.objectId())).isEmpty()
        );
    }

    @Test
    void bindInternalReferenceShouldHideForeignStoredSubjectOnReplayWithoutMutation() {
        Fixture fixture = fixture();
        UUID referenceId = uuid(81);
        OssObjectReference foreignReference = OssObjectReference.active(
                referenceId,
                fixture.objectId(),
                fixture.versionId(),
                "profile-service",
                "profile",
                "avatar",
                "upload-7",
                "PRIMARY",
                CLOCK.instant(),
                null
        );
        fixture.referenceRepository.rows.put(referenceId, foreignReference);

        Throwable failure = catchThrowable(() -> fixture.serviceAt(CLOCK.instant()).bindInternalReference(
                "community-app",
                deterministicCommand(
                        referenceId,
                        fixture.objectId(),
                        fixture.versionId(),
                        "upload-7",
                        "PRIMARY",
                        null,
                        "user-7")));

        assertAll(
                () -> assertHiddenObjectNotFound(failure),
                () -> assertThat(fixture.referenceRepository.saveCount()).isZero(),
                () -> assertThat(fixture.referenceRepository.findById(referenceId)).contains(foreignReference)
        );
    }

    @Test
    void bindInternalReferenceShouldReplayMatchingVersionWithoutMutation() {
        Fixture fixture = fixture();
        UUID referenceId = uuid(89);
        BindObjectReferenceCommand command = deterministicCommand(
                referenceId,
                fixture.objectId(),
                fixture.versionId(),
                "upload-7",
                "PRIMARY",
                null,
                "user-7");

        ObjectReferenceResult first = fixture.serviceAt(CLOCK.instant()).bindInternalReference(
                "community-app",
                command);
        ObjectReferenceResult replayed = fixture.serviceAt(CLOCK.instant().plusSeconds(60)).bindInternalReference(
                "community-app",
                command);

        assertAll(
                () -> assertThat(replayed.referenceId()).isEqualTo(first.referenceId()),
                () -> assertThat(replayed.versionId()).isEqualTo(fixture.versionId()),
                () -> assertThat(replayed.createdAt()).isEqualTo(first.createdAt()),
                () -> assertThat(fixture.referenceRepository.findByObjectId(fixture.objectId())).hasSize(1),
                () -> assertThat(fixture.referenceRepository.saveCount()).isEqualTo(1)
        );
    }

    @Test
    void getAndReleaseInternalReferenceShouldHideForeignReferenceSubjectWithoutMutation() {
        Fixture fixture = fixture();
        UUID referenceId = uuid(81);
        OssObjectReference foreignReference = OssObjectReference.active(
                referenceId,
                fixture.objectId(),
                fixture.versionId(),
                "profile-service",
                "profile",
                "avatar",
                "upload-7",
                "PRIMARY",
                CLOCK.instant(),
                null
        );
        fixture.referenceRepository.rows.put(referenceId, foreignReference);

        Throwable getFailure = catchThrowable(() -> fixture.serviceAt(CLOCK.instant()).getInternalReference(
                fixture.objectId(),
                referenceId,
                "community-app"));
        Throwable releaseFailure = catchThrowable(() -> fixture.serviceAt(CLOCK.instant()).releaseInternalReference(
                "community-app",
                new ReleaseObjectReferenceCommand(fixture.objectId(), referenceId, "user-7")));

        assertAll(
                () -> assertHiddenObjectNotFound(getFailure),
                () -> assertHiddenObjectNotFound(releaseFailure),
                () -> assertThat(fixture.referenceRepository.saveCount()).isZero(),
                () -> assertThat(fixture.referenceRepository.findById(referenceId)).contains(foreignReference)
        );
    }

    @Test
    void internalReferenceEntriesShouldNotInvokePublicReferenceEntries() {
        Fixture fixture = fixture();
        UUID referenceId = uuid(87);
        OssObjectReference stored = OssObjectReference.active(
                referenceId,
                fixture.objectId(),
                fixture.versionId(),
                "community-app",
                "community-app",
                "user",
                "upload-7",
                "PRIMARY",
                CLOCK.instant(),
                null
        );
        fixture.referenceRepository.rows.put(referenceId, stored);
        AtomicInteger bindCalls = new AtomicInteger();
        AtomicInteger findCalls = new AtomicInteger();
        AtomicInteger releaseCalls = new AtomicInteger();
        ObjectReferenceApplicationService service = new ObjectReferenceApplicationService(
                fixture.objectRepository,
                fixture.versionRepository,
                fixture.referenceRepository,
                CLOCK
        ) {
            @Override
            public ObjectReferenceResult bindReference(BindObjectReferenceCommand command) {
                bindCalls.incrementAndGet();
                return super.bindReference(command);
            }

            @Override
            public ObjectReferenceResult findReference(UUID objectId, UUID referenceId) {
                findCalls.incrementAndGet();
                return super.findReference(objectId, referenceId);
            }

            @Override
            public ObjectReferenceResult releaseReference(ReleaseObjectReferenceCommand command) {
                releaseCalls.incrementAndGet();
                return super.releaseReference(command);
            }
        };

        service.bindInternalReference(
                "community-app",
                deterministicCommand(
                        uuid(88),
                        fixture.objectId(),
                        fixture.versionId(),
                        "upload-7",
                        "PRIMARY",
                        null,
                        "user-7"));
        service.getInternalReference(fixture.objectId(), referenceId, "community-app");
        service.releaseInternalReference(
                "community-app",
                new ReleaseObjectReferenceCommand(fixture.objectId(), referenceId, "user-7"));

        assertAll(
                () -> assertThat(bindCalls).hasValue(0),
                () -> assertThat(findCalls).hasValue(0),
                () -> assertThat(releaseCalls).hasValue(0)
        );
    }

    @Test
    void bindAndReleaseShouldPersistReferenceLifecycle() {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeReferenceRepository referenceRepository = new FakeReferenceRepository();
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
                "avatar",
                "7",
                OssVisibility.PUBLIC,
                "7",
                CLOCK.instant()
        ).activate(version, CLOCK.instant()));
        FakeVersionRepository versionRepository = new FakeVersionRepository();
        versionRepository.save(version);
        ObjectReferenceApplicationService service = new ObjectReferenceApplicationService(
                objectRepository,
                versionRepository,
                referenceRepository,
                CLOCK
        );

        ObjectReferenceResult bound = service.bindReference(new BindObjectReferenceCommand(
                objectId,
                null,
                "community-app",
                "user",
                "avatar",
                "7",
                "PRIMARY",
                CLOCK.instant().plusSeconds(3600),
                "7"
        ));
        ObjectReferenceResult released = service.releaseReference(new ReleaseObjectReferenceCommand(
                objectId,
                bound.referenceId(),
                "7"
        ));

        assertThat(bound.status()).isEqualTo("ACTIVE");
        assertThat(bound.referenceId()).isNotNull();
        assertThat(referenceRepository.findByObjectId(objectId)).hasSize(1);
        assertThat(released.status()).isEqualTo("RELEASED");
        assertThat(released.releasedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void bindReferenceShouldRejectVersionFromDifferentObject() {
        UUID objectId = uuid(1);
        UUID otherObjectId = uuid(3);
        UUID versionId = uuid(2);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeVersionRepository versionRepository = new FakeVersionRepository();
        FakeReferenceRepository referenceRepository = new FakeReferenceRepository();
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
        ).activate(activeVersion(objectId, versionId), CLOCK.instant()));
        versionRepository.save(activeVersion(otherObjectId, versionId));
        ObjectReferenceApplicationService service = new ObjectReferenceApplicationService(
                objectRepository,
                versionRepository,
                referenceRepository,
                CLOCK
        );

        assertThatThrownBy(() -> service.bindReference(new BindObjectReferenceCommand(
                objectId,
                versionId,
                "community-app",
                "user",
                "avatar",
                "7",
                "PRIMARY",
                null,
                "7"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("object version does not belong to object");
    }

    @Test
    void requestedIdShouldMakeResponseLossRetryReturnTheOriginalReference() {
        Fixture fixture = fixture();
        UUID referenceId = uuid(5);
        BindObjectReferenceCommand firstAttempt = deterministicCommand(
                referenceId, fixture.objectId(), fixture.versionId(), "7", "PRIMARY",
                CLOCK.instant().plusSeconds(3600), "request-actor");

        fixture.serviceAt(CLOCK.instant()).bindReference(firstAttempt);
        ObjectReferenceResult retried = fixture.serviceAt(CLOCK.instant().plusSeconds(120)).bindReference(
                deterministicCommand(
                        referenceId, fixture.objectId(), fixture.versionId(), "7", "PRIMARY",
                        CLOCK.instant().plusSeconds(3600), "retry-actor"));

        assertThat(retried.referenceId()).isEqualTo(referenceId);
        assertThat(retried.createdAt()).isEqualTo(CLOCK.instant());
        assertThat(retried.status()).isEqualTo("ACTIVE");
        assertThat(fixture.referenceRepository().findByObjectId(fixture.objectId())).hasSize(1);
        assertThat(fixture.referenceRepository().saveCount()).isEqualTo(1);
    }

    @Test
    void sameRequestedIdWithDifferentSemanticFingerprintShouldConflictWithoutMutation() {
        Fixture fixture = fixture();
        UUID referenceId = uuid(5);
        fixture.serviceAt(CLOCK.instant()).bindReference(deterministicCommand(
                referenceId, fixture.objectId(), fixture.versionId(), "7", "PRIMARY",
                CLOCK.instant().plusSeconds(3600), "7"));

        assertReferenceConflict(() -> fixture.serviceAt(CLOCK.instant().plusSeconds(1)).bindReference(
                deterministicCommand(
                        referenceId, fixture.objectId(), fixture.versionId(), "8", "PRIMARY",
                        CLOCK.instant().plusSeconds(3600), "8")));
        assertReferenceConflict(() -> fixture.serviceAt(CLOCK.instant().plusSeconds(2)).bindReference(
                deterministicCommand(
                        referenceId, fixture.objectId(), fixture.versionId(), "7", "SECONDARY",
                        CLOCK.instant().plusSeconds(7200), "7")));

        OssObjectReference stored = fixture.referenceRepository().findById(referenceId).orElseThrow();
        assertThat(stored.subjectId()).isEqualTo("7");
        assertThat(stored.referenceRole()).isEqualTo("PRIMARY");
        assertThat(stored.retainUntil()).isEqualTo(CLOCK.instant().plusSeconds(3600));
        assertThat(fixture.referenceRepository().saveCount()).isEqualTo(1);
    }

    @Test
    void lateBindAfterReleaseAndRepeatedReleaseShouldRemainReleasedAtTheFirstTimestamp() {
        Fixture fixture = fixture();
        UUID referenceId = uuid(5);
        BindObjectReferenceCommand bind = deterministicCommand(
                referenceId, fixture.objectId(), fixture.versionId(), "7", "PRIMARY", null, "7");
        fixture.serviceAt(CLOCK.instant()).bindReference(bind);

        Instant firstReleaseAt = CLOCK.instant().plusSeconds(60);
        ObjectReferenceResult firstRelease = fixture.serviceAt(firstReleaseAt).releaseReference(
                new ReleaseObjectReferenceCommand(fixture.objectId(), referenceId, "7"));
        ObjectReferenceResult lateBind = fixture.serviceAt(firstReleaseAt.plusSeconds(60)).bindReference(bind);
        ObjectReferenceResult repeatedRelease = fixture.serviceAt(firstReleaseAt.plusSeconds(120)).releaseReference(
                new ReleaseObjectReferenceCommand(fixture.objectId(), referenceId, "retry-actor"));

        assertThat(firstRelease.status()).isEqualTo("RELEASED");
        assertThat(lateBind.status()).isEqualTo("RELEASED");
        assertThat(repeatedRelease.status()).isEqualTo("RELEASED");
        assertThat(firstRelease.releasedAt()).isEqualTo(firstReleaseAt);
        assertThat(lateBind.releasedAt()).isEqualTo(firstReleaseAt);
        assertThat(repeatedRelease.releasedAt()).isEqualTo(firstReleaseAt);
        assertThat(fixture.referenceRepository().saveCount()).isEqualTo(2);
    }

    private static void assertReferenceConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode().getKind()).isEqualTo(ErrorKind.CONFLICT);
                    assertThat(exception.getMessage()).contains("reference").contains("conflict");
                });
    }

    private static void assertHiddenObjectNotFound(Throwable throwable) {
        assertThat(throwable).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND);
            assertThat(exception.getMessage()).isEqualTo("OSS object not found");
        });
    }

    private static BindObjectReferenceCommand deterministicCommand(
            UUID referenceId,
            UUID objectId,
            UUID versionId,
            String subjectId,
            String referenceRole,
            Instant retainUntil,
            String actorId
    ) {
        return new BindObjectReferenceCommand(
                referenceId,
                objectId,
                versionId,
                "community-app",
                "user",
                "avatar",
                subjectId,
                referenceRole,
                retainUntil,
                actorId
        );
    }

    private static Fixture fixture() {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeVersionRepository versionRepository = new FakeVersionRepository();
        FakeReferenceRepository referenceRepository = new FakeReferenceRepository();
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
        return new Fixture(objectId, versionId, objectRepository, versionRepository, referenceRepository);
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

    private static final class FakeReferenceRepository implements OssObjectReferenceRepository {
        private final Map<UUID, OssObjectReference> rows = new HashMap<>();
        private int saveCount;

        @Override
        public void save(OssObjectReference reference) {
            rows.put(reference.referenceId(), reference);
            saveCount++;
        }

        @Override
        public OssObjectReference insertOrFindExisting(OssObjectReference reference) {
            OssObjectReference existing = rows.get(reference.referenceId());
            if (existing != null) {
                return existing;
            }
            save(reference);
            return reference;
        }

        @Override
        public Optional<OssObjectReference> findById(UUID referenceId) {
            return Optional.ofNullable(rows.get(referenceId));
        }

        @Override
        public List<OssObjectReference> findByObjectId(UUID objectId) {
            return rows.values().stream().filter(reference -> objectId.equals(reference.objectId())).toList();
        }

        int saveCount() {
            return saveCount;
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

    private record Fixture(
            UUID objectId,
            UUID versionId,
            FakeObjectRepository objectRepository,
            FakeVersionRepository versionRepository,
            FakeReferenceRepository referenceRepository
    ) {
        ObjectReferenceApplicationService serviceAt(Instant instant) {
            return new ObjectReferenceApplicationService(
                    objectRepository,
                    versionRepository,
                    referenceRepository,
                    Clock.fixed(instant, ZoneOffset.UTC)
            );
        }
    }
}
