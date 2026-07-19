package com.nowcoder.community.oss.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.spring.policy.UploadPolicyDecisions;
import com.nowcoder.community.common.spring.policy.UploadPolicyProperties;
import com.nowcoder.community.common.spring.feature.FeatureFlagDecisions;
import com.nowcoder.community.common.spring.feature.FeatureFlagProperties;
import com.nowcoder.community.oss.application.command.CompleteObjectUploadCommand;
import com.nowcoder.community.oss.application.command.ObjectUploadContent;
import com.nowcoder.community.oss.application.command.PrepareObjectUploadCommand;
import com.nowcoder.community.oss.application.result.ObjectMetadataResult;
import com.nowcoder.community.oss.application.result.ObjectUploadSessionResult;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectStatus;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssUploadSession;
import com.nowcoder.community.oss.domain.model.OssUploadSessionStatus;
import com.nowcoder.community.oss.domain.model.OssUsagePolicy;
import com.nowcoder.community.oss.domain.model.OssVisibility;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

class ObjectUploadApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-07T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void prepareInternalUploadShouldHideForeignOwnerWithoutEffects() {
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeUploadSessionRepository sessionRepository = new FakeUploadSessionRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        ObjectUploadApplicationService service = new ObjectUploadApplicationService(
                objectRepository,
                versionRepository,
                sessionRepository,
                objectStore,
                "community-oss",
                "http://localhost:12880",
                CLOCK
        );

        Throwable failure = catchThrowable(() -> service.prepareInternalUpload(
                "profile-service",
                new PrepareObjectUploadCommand(
                        uuid(800),
                        "DRIVE_FILE",
                        "community-app",
                        "drive",
                        "DRIVE_UPLOAD",
                        "upload-7",
                        "PRIVATE",
                        "note.txt",
                        "text/plain",
                        2,
                        "sha256-note",
                        "user-7"
                )));

        assertAll(
                () -> assertHiddenObjectNotFound(failure),
                () -> assertThat(objectRepository.saveCount).isZero(),
                () -> assertThat(versionRepository.saveCount).isZero(),
                () -> assertThat(sessionRepository.mutationCount).isZero(),
                () -> assertThat(objectStore.operationCount).isZero()
        );
    }

    @Test
    void prepareInternalUploadShouldHideUserOwnershipWithoutEffects() {
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeUploadSessionRepository sessionRepository = new FakeUploadSessionRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        ObjectUploadApplicationService service = new ObjectUploadApplicationService(
                objectRepository,
                versionRepository,
                sessionRepository,
                objectStore,
                "community-oss",
                "http://localhost:12880",
                CLOCK
        );

        Throwable failure = catchThrowable(() -> service.prepareInternalUpload(
                "community-app",
                new PrepareObjectUploadCommand(
                        uuid(801),
                        "USER_AVATAR",
                        "community-app",
                        "user",
                        "USER",
                        "user-7",
                        "PRIVATE",
                        "avatar.png",
                        "image/png",
                        2,
                        "sha256-avatar",
                        "user-7"
                )));

        assertAll(
                () -> assertHiddenObjectNotFound(failure),
                () -> assertThat(objectRepository.saveCount).isZero(),
                () -> assertThat(versionRepository.saveCount).isZero(),
                () -> assertThat(sessionRepository.mutationCount).isZero(),
                () -> assertThat(objectStore.operationCount).isZero()
        );
    }

    @Test
    void prepareInternalUploadShouldPreserveActorAndReturnInternalCompletionUrl() {
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeUploadSessionRepository sessionRepository = new FakeUploadSessionRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        ObjectUploadApplicationService service = new ObjectUploadApplicationService(
                objectRepository,
                versionRepository,
                sessionRepository,
                objectStore,
                "community-oss",
                "http://localhost:12880",
                CLOCK
        );

        ObjectUploadSessionResult prepared = service.prepareInternalUpload(
                "community-app",
                new PrepareObjectUploadCommand(
                        uuid(802),
                        "DRIVE_FILE",
                        "community-app",
                        "drive",
                        "DRIVE_UPLOAD",
                        "upload-7",
                        "PRIVATE",
                        "note.txt",
                        "text/plain",
                        2,
                        "sha256-note",
                        "user-7"
                ));
        OssUploadSession session = sessionRepository.findById(prepared.sessionId()).orElseThrow();

        assertAll(
                () -> assertThat(prepared.uploadUrl()).isEqualTo(
                        "/internal/oss/upload-sessions/" + prepared.sessionId() + "/complete"),
                () -> assertThat(session.ownerService()).isEqualTo("community-app"),
                () -> assertThat(session.ownerType()).isEqualTo("DRIVE_UPLOAD"),
                () -> assertThat(session.createdBy()).isEqualTo("user-7"),
                () -> assertThat(objectStore.operationCount).isZero()
        );
    }

    @Test
    void completeInternalUploadShouldHideForeignServiceWithoutEffects() {
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeUploadSessionRepository sessionRepository = new FakeUploadSessionRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        ObjectUploadApplicationService service = new ObjectUploadApplicationService(
                objectRepository,
                versionRepository,
                sessionRepository,
                objectStore,
                "community-oss",
                "http://localhost:12880",
                CLOCK
        );
        ObjectUploadSessionResult prepared = service.prepareInternalUpload(
                "community-app",
                new PrepareObjectUploadCommand(
                        uuid(810),
                        "DRIVE_FILE",
                        "community-app",
                        "drive",
                        "DRIVE_UPLOAD",
                        "upload-7",
                        "PRIVATE",
                        "note.txt",
                        "text/plain",
                        2,
                        "sha256-note",
                        "user-7"
                ));
        objectRepository.saveCount = 0;
        versionRepository.saveCount = 0;
        sessionRepository.mutationCount = 0;
        objectStore.operationCount = 0;
        AtomicInteger streamOpenCount = new AtomicInteger();

        Throwable failure = catchThrowable(() -> service.completeInternalUpload(
                "profile-service",
                completeCommand(prepared, streamOpenCount)));

        assertAll(
                () -> assertHiddenObjectNotFound(failure),
                () -> assertThat(streamOpenCount).hasValue(0),
                () -> assertThat(objectRepository.saveCount).isZero(),
                () -> assertThat(versionRepository.saveCount).isZero(),
                () -> assertThat(sessionRepository.mutationCount).isZero(),
                () -> assertThat(objectStore.operationCount).isZero(),
                () -> assertThat(sessionRepository.findById(prepared.sessionId()).orElseThrow().status())
                        .isEqualTo(OssUploadSessionStatus.READY)
        );
    }

    @Test
    void completeInternalUploadShouldHideForeignObjectOwnerWithoutEffects() {
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeUploadSessionRepository sessionRepository = new FakeUploadSessionRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        ObjectUploadApplicationService service = new ObjectUploadApplicationService(
                objectRepository,
                versionRepository,
                sessionRepository,
                objectStore,
                "community-oss",
                "http://localhost:12880",
                CLOCK
        );
        ObjectUploadSessionResult prepared = service.prepareInternalUpload(
                "community-app",
                new PrepareObjectUploadCommand(
                        uuid(811),
                        "DRIVE_FILE",
                        "community-app",
                        "drive",
                        "DRIVE_UPLOAD",
                        "upload-7",
                        "PRIVATE",
                        "note.txt",
                        "text/plain",
                        2,
                        "sha256-note",
                        "user-7"
                ));
        objectRepository.rows.put(prepared.objectId(), OssObject.stage(
                prepared.objectId(),
                "DRIVE_FILE",
                "profile-service",
                "drive",
                "DRIVE_UPLOAD",
                "upload-7",
                OssVisibility.PRIVATE,
                "user-7",
                CLOCK.instant()
        ));
        objectRepository.saveCount = 0;
        versionRepository.saveCount = 0;
        sessionRepository.mutationCount = 0;
        objectStore.operationCount = 0;
        AtomicInteger streamOpenCount = new AtomicInteger();

        Throwable failure = catchThrowable(() -> service.completeInternalUpload(
                "community-app",
                completeCommand(prepared, streamOpenCount)));

        assertAll(
                () -> assertHiddenObjectNotFound(failure),
                () -> assertThat(streamOpenCount).hasValue(0),
                () -> assertThat(objectRepository.saveCount).isZero(),
                () -> assertThat(versionRepository.saveCount).isZero(),
                () -> assertThat(sessionRepository.mutationCount).isZero(),
                () -> assertThat(objectStore.operationCount).isZero(),
                () -> assertThat(sessionRepository.findById(prepared.sessionId()).orElseThrow().status())
                        .isEqualTo(OssUploadSessionStatus.READY)
        );
    }

    @Test
    void completeInternalUploadShouldHideUserOwnedSessionWithoutEffects() {
        InternalUploadFixture fixture = internalUploadFixture(812);
        OssUploadSession session = fixture.sessionRepository.findById(fixture.prepared.sessionId()).orElseThrow();
        fixture.sessionRepository.rows.put(session.sessionId(), OssUploadSession.ready(
                session.requestId(),
                session.sessionId(),
                session.objectId(),
                session.versionId(),
                session.uploadMode(),
                session.ownerService(),
                session.ownerDomain(),
                "USER",
                session.ownerId(),
                session.expectedFileName(),
                session.expectedContentType(),
                session.expectedContentLength(),
                session.expectedChecksumSha256(),
                session.createdBy(),
                session.createdAt(),
                session.expiresAt()
        ));
        fixture.resetEffects();
        AtomicInteger streamOpenCount = new AtomicInteger();

        Throwable failure = catchThrowable(() -> fixture.service.completeInternalUpload(
                "community-app",
                completeCommand(fixture.prepared, streamOpenCount)));

        assertInternalCompletionHiddenWithoutEffects(fixture, failure, streamOpenCount);
    }

    @Test
    void completeInternalUploadShouldHideUserOwnedObjectWithoutEffects() {
        InternalUploadFixture fixture = internalUploadFixture(813);
        fixture.objectRepository.rows.put(fixture.prepared.objectId(), OssObject.stage(
                fixture.prepared.objectId(),
                "DRIVE_FILE",
                "community-app",
                "drive",
                "USER",
                "user-7",
                OssVisibility.PRIVATE,
                "user-7",
                CLOCK.instant()
        ));
        fixture.resetEffects();
        AtomicInteger streamOpenCount = new AtomicInteger();

        Throwable failure = catchThrowable(() -> fixture.service.completeInternalUpload(
                "community-app",
                completeCommand(fixture.prepared, streamOpenCount)));

        assertInternalCompletionHiddenWithoutEffects(fixture, failure, streamOpenCount);
    }

    @Test
    void completeInternalUploadShouldHideMismatchedSessionVersionWithoutEffects() {
        InternalUploadFixture fixture = internalUploadFixture(814);
        AtomicInteger streamOpenCount = new AtomicInteger();
        CompleteObjectUploadCommand mismatched = new CompleteObjectUploadCommand(
                fixture.prepared.sessionId(),
                fixture.prepared.objectId(),
                uuid(999),
                uploadContent(streamOpenCount),
                "user-7"
        );

        Throwable failure = catchThrowable(() -> fixture.service.completeInternalUpload(
                "community-app",
                mismatched));

        assertInternalCompletionHiddenWithoutEffects(fixture, failure, streamOpenCount);
    }

    @Test
    void completeInternalUploadShouldUsePersistedSessionActorWhenIncomingActorDiffers() {
        InternalUploadFixture fixture = internalUploadFixture(815);
        AtomicInteger streamOpenCount = new AtomicInteger();
        CompleteObjectUploadCommand incoming = new CompleteObjectUploadCommand(
                fixture.prepared.sessionId(),
                fixture.prepared.objectId(),
                fixture.prepared.versionId(),
                uploadContent(streamOpenCount),
                "internal-upload-placeholder"
        );

        ObjectMetadataResult completed = fixture.service.completeInternalUpload(
                "community-app",
                incoming);

        assertAll(
                () -> assertThat(incoming.actorId()).isNotEqualTo(
                        fixture.sessionRepository.findById(fixture.prepared.sessionId()).orElseThrow().createdBy()),
                () -> assertThat(completed.status()).isEqualTo(OssObjectStatus.ACTIVE.name()),
                () -> assertThat(completed.ownerService()).isEqualTo("community-app"),
                () -> assertThat(completed.ownerId()).isEqualTo("upload-7"),
                () -> assertThat(streamOpenCount).hasValue(1),
                () -> assertThat(fixture.sessionRepository.findById(fixture.prepared.sessionId()).orElseThrow().createdBy())
                        .isEqualTo("user-7"),
                () -> assertThat(fixture.sessionRepository.findById(fixture.prepared.sessionId()).orElseThrow().status())
                        .isEqualTo(OssUploadSessionStatus.COMPLETED)
        );
    }

    @Test
    void prepareAndCompleteProxyUploadShouldActivateVersionAndReturnCanonicalPublicUrl() {
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeUploadSessionRepository uploadSessionRepository = new FakeUploadSessionRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        ObjectUploadApplicationService service = new ObjectUploadApplicationService(
                objectRepository,
                versionRepository,
                uploadSessionRepository,
                objectStore,
                "community-oss",
                "http://localhost:12880",
                CLOCK
        );
        UUID ownerId = uuid(7);

        ObjectUploadSessionResult prepared = service.prepareUpload(new PrepareObjectUploadCommand(
                "USER_AVATAR",
                "community-app",
                "user",
                "POST",
                "post-42",
                "PUBLIC",
                "avatar.png",
                "image/png",
                6,
                "sha256-avatar",
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
                ),
                ownerId.toString()
        ));

        assertThat(completed.status()).isEqualTo(OssObjectStatus.ACTIVE.name());
        assertThat(completed.currentVersionId()).isEqualTo(prepared.versionId());
        assertThat(completed.publicUrl()).isEqualTo(
                "http://localhost:12880/files/" + prepared.objectId() + "/" + prepared.versionId() + "/avatar.png"
        );
        assertThat(objectStore.capturedKey).isEqualTo(
                "objects/" + prepared.objectId() + "/" + prepared.versionId() + "/avatar.png.claim-1"
        );
        assertThat(uploadSessionRepository.findById(prepared.sessionId()).orElseThrow().status())
                .isEqualTo(OssUploadSessionStatus.COMPLETED);
    }

    @Test
    void completeUploadShouldRejectActorDifferentFromSessionCreatorBeforeClaimOrStorageWrite() {
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeUploadSessionRepository uploadSessionRepository = new FakeUploadSessionRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        ObjectUploadApplicationService service = new ObjectUploadApplicationService(
                objectRepository,
                versionRepository,
                uploadSessionRepository,
                objectStore,
                "community-oss",
                "http://localhost:12880",
                CLOCK
        );
        ObjectUploadSessionResult prepared = service.prepareUpload(new PrepareObjectUploadCommand(
                "USER_AVATAR",
                "community-app",
                "user",
                "USER",
                "owner-7",
                "PUBLIC",
                "avatar.png",
                "image/png",
                6,
                "sha256-avatar",
                "creator-7"
        ));
        objectRepository.saveCount = 0;
        versionRepository.saveCount = 0;

        assertThatThrownBy(() -> service.completeUpload(new CompleteObjectUploadCommand(
                prepared.sessionId(),
                prepared.objectId(),
                prepared.versionId(),
                new ObjectUploadContent(
                        () -> new ByteArrayInputStream("avatar".getBytes(StandardCharsets.UTF_8)),
                        "image/png",
                        6,
                        "sha256-avatar"
                ),
                "attacker-9"
        )))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND);
                    assertThat(exception.getMessage()).isEqualTo("OSS object not found");
                });

        assertThat(objectStore.capturedKey).isNull();
        assertThat(uploadSessionRepository.findById(prepared.sessionId()).orElseThrow().status())
                .isEqualTo(OssUploadSessionStatus.READY);
        assertThat(objectRepository.findById(prepared.objectId()).orElseThrow().status())
                .isEqualTo(OssObjectStatus.STAGED);
        assertThat(versionRepository.findById(prepared.versionId()).orElseThrow().status())
                .isEqualTo(com.nowcoder.community.oss.domain.model.OssObjectVersionStatus.STAGED);
        assertThat(objectRepository.saveCount).isZero();
        assertThat(versionRepository.saveCount).isZero();
    }

    @Test
    void completedUploadReplayShouldHideMissingObjectWithoutSideEffects() {
        CompletedReplayFixture fixture = completedReplayFixture();
        fixture.objectRepository.rows.remove(fixture.objectId);

        assertCompletedReplayHiddenWithoutSideEffects(fixture);
    }

    @Test
    void completedUploadReplayShouldHideMissingVersionWithoutSideEffects() {
        CompletedReplayFixture fixture = completedReplayFixture();
        fixture.versionRepository.rows.remove(fixture.versionId);

        assertCompletedReplayHiddenWithoutSideEffects(fixture);
    }

    @Test
    void completedUploadReplayShouldHideMismatchedCurrentVersionWithoutSideEffects() {
        CompletedReplayFixture fixture = completedReplayFixture();
        OssObject current = fixture.objectRepository.rows.get(fixture.objectId);
        current = current.activate(
                activeVersion(fixture.objectId, uuid(704)),
                CLOCK.instant().plusSeconds(2));
        fixture.objectRepository.rows.put(fixture.objectId, current);

        assertCompletedReplayHiddenWithoutSideEffects(fixture);
    }

    @Test
    void completedUploadReplayShouldHideCrossObjectVersionWithoutSideEffects() {
        CompletedReplayFixture fixture = completedReplayFixture();
        fixture.versionRepository.rows.put(
                fixture.versionId,
                activeVersion(uuid(705), fixture.versionId));

        assertCompletedReplayHiddenWithoutSideEffects(fixture);
    }

    @Test
    void completedUploadReplayShouldReturnCanonicalMetadataWithoutSideEffects() {
        CompletedReplayFixture fixture = completedReplayFixture();
        AtomicInteger streamOpenCount = new AtomicInteger();

        ObjectMetadataResult result = fixture.service.completeUpload(
                completedReplayCommand(fixture, streamOpenCount));

        assertThat(result.objectId()).isEqualTo(fixture.objectId);
        assertThat(result.currentVersionId()).isEqualTo(fixture.versionId);
        assertThat(result.status()).isEqualTo(OssObjectStatus.ACTIVE.name());
        assertCompletedReplayHasNoSideEffects(fixture, streamOpenCount);
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
                "7"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("upload exceeds usage policy maxBytes");
    }

    @Test
    void prepareUploadShouldRejectContentThatViolatesNacosUploadPolicy() {
        UploadPolicyProperties uploadPolicy = new UploadPolicyProperties();
        uploadPolicy.setAllowedMimeTypes(List.of("image/png"));
        uploadPolicy.setAllowedExtensions(List.of("png"));
        uploadPolicy.setMaxFileSize(org.springframework.util.unit.DataSize.ofBytes(5));
        ObjectUploadApplicationService service = new ObjectUploadApplicationService(
                new FakeObjectRepository(),
                new FakeObjectVersionRepository(),
                new FakeUploadSessionRepository(),
                null,
                new CapturingObjectStore(),
                "community-oss",
                "http://localhost:12880",
                CLOCK,
                new UploadPolicyDecisions(uploadPolicy)
        );

        assertThatThrownBy(() -> service.prepareUpload(new PrepareObjectUploadCommand(
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                "7",
                "PUBLIC",
                "avatar.exe",
                "application/x-msdownload",
                6,
                "",
                "7"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("upload exceeds global max file size");

        assertThatThrownBy(() -> service.prepareUpload(new PrepareObjectUploadCommand(
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                "7",
                "PUBLIC",
                "avatar.exe",
                "image/png",
                5,
                "",
                "7"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file extension is not allowed by global upload policy");
    }

    @Test
    void prepareUploadShouldRejectWhenNacosFileUploadFeatureIsDisabled() {
        FeatureFlagProperties flags = new FeatureFlagProperties();
        flags.getFlags().put("file-upload", false);
        ObjectUploadApplicationService service = new ObjectUploadApplicationService(
                new FakeObjectRepository(),
                new FakeObjectVersionRepository(),
                new FakeUploadSessionRepository(),
                null,
                new CapturingObjectStore(),
                "community-oss",
                "http://localhost:12880",
                CLOCK,
                new UploadPolicyDecisions(new UploadPolicyProperties()),
                new FeatureFlagDecisions(flags)
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
                5,
                "",
                "7"
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessage("file upload is disabled by feature flag");
    }

    @Test
    void prepareUploadShouldRejectWhenNacosAvatarOrMediaUploadPolicyIsDisabled() {
        UploadPolicyProperties avatarPolicy = new UploadPolicyProperties();
        avatarPolicy.setAvatarUploadEnabled(false);
        ObjectUploadApplicationService avatarService = serviceWithUploadPolicy(avatarPolicy);

        assertThatThrownBy(() -> avatarService.prepareUpload(new PrepareObjectUploadCommand(
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                "7",
                "PUBLIC",
                "avatar.png",
                "image/png",
                5,
                "",
                "7"
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessage("avatar upload is disabled by upload policy");

        UploadPolicyProperties mediaPolicy = new UploadPolicyProperties();
        mediaPolicy.setMediaUploadEnabled(false);
        ObjectUploadApplicationService mediaService = serviceWithUploadPolicy(mediaPolicy);

        assertThatThrownBy(() -> mediaService.prepareUpload(new PrepareObjectUploadCommand(
                "DRIVE_FILE",
                "community-app",
                "drive",
                "drive-upload",
                "7",
                "PRIVATE",
                "file.txt",
                "text/plain",
                5,
                "",
                "7"
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessage("media upload is disabled by upload policy");
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
                ),
                "7"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("upload exceeds usage policy maxBytes");
        assertThat(objectStore.capturedKey).isNull();
    }

    @Test
    void completeUploadShouldRejectWhenNacosMediaUploadPolicyIsDisabledAfterPrepare() {
        UploadPolicyProperties uploadPolicy = new UploadPolicyProperties();
        UploadPolicyDecisions uploadPolicyDecisions = new UploadPolicyDecisions(uploadPolicy);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeUploadSessionRepository uploadSessionRepository = new FakeUploadSessionRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        ObjectUploadApplicationService service = new ObjectUploadApplicationService(
                objectRepository,
                versionRepository,
                uploadSessionRepository,
                null,
                objectStore,
                "community-oss",
                "http://localhost:12880",
                CLOCK,
                uploadPolicyDecisions
        );
        ObjectUploadSessionResult prepared = service.prepareUpload(new PrepareObjectUploadCommand(
                "DRIVE_FILE",
                "community-app",
                "drive",
                "drive-upload",
                "7",
                "PRIVATE",
                "file.txt",
                "text/plain",
                5,
                "",
                "7"
        ));
        uploadPolicy.setMediaUploadEnabled(false);

        assertThatThrownBy(() -> service.completeUpload(new CompleteObjectUploadCommand(
                prepared.sessionId(),
                prepared.objectId(),
                prepared.versionId(),
                new ObjectUploadContent(
                        () -> new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)),
                        "text/plain",
                        5,
                        ""
                ),
                "7"
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessage("media upload is disabled by upload policy");
        assertThat(objectStore.capturedKey).isNull();
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static CompletedReplayFixture completedReplayFixture() {
        UUID objectId = uuid(701);
        UUID versionId = uuid(702);
        UUID sessionId = uuid(703);
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeUploadSessionRepository sessionRepository = new FakeUploadSessionRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        OssObjectVersion version = activeVersion(objectId, versionId);
        OssObject object = OssObject.stage(
                objectId,
                "USER_AVATAR",
                "community-app",
                "user",
                "USER",
                "owner-7",
                OssVisibility.PUBLIC,
                "creator-7",
                CLOCK.instant()
        ).activate(version, CLOCK.instant().plusSeconds(1));
        OssUploadSession session = OssUploadSession.ready(
                sessionId,
                objectId,
                versionId,
                "PROXY",
                "community-app",
                "user",
                "USER",
                "owner-7",
                "avatar.png",
                "image/png",
                6,
                "sha256-avatar",
                "creator-7",
                CLOCK.instant(),
                CLOCK.instant().plusSeconds(900)
        ).complete(CLOCK.instant().plusSeconds(1));
        objectRepository.rows.put(objectId, object);
        versionRepository.rows.put(versionId, version);
        sessionRepository.rows.put(sessionId, session);
        ObjectUploadApplicationService service = new ObjectUploadApplicationService(
                objectRepository,
                versionRepository,
                sessionRepository,
                objectStore,
                "community-oss",
                "http://localhost:12880",
                CLOCK
        );
        return new CompletedReplayFixture(
                objectId,
                versionId,
                sessionId,
                objectRepository,
                versionRepository,
                sessionRepository,
                objectStore,
                service
        );
    }

    private static OssObjectVersion activeVersion(UUID objectId, UUID versionId) {
        return OssObjectVersion.staged(
                versionId,
                objectId,
                "S3_COMPATIBLE",
                "community-oss",
                "objects/" + objectId + "/" + versionId + "/avatar.png",
                "avatar.png",
                "image/png",
                6,
                "sha256-avatar",
                CLOCK.instant()
        ).withUploadedContent("image/png", 6, "sha256-avatar")
                .activate("etag-avatar", CLOCK.instant().plusSeconds(1));
    }

    private static CompleteObjectUploadCommand completedReplayCommand(
            CompletedReplayFixture fixture,
            AtomicInteger streamOpenCount
    ) {
        return new CompleteObjectUploadCommand(
                fixture.sessionId,
                fixture.objectId,
                fixture.versionId,
                new ObjectUploadContent(
                        () -> {
                            streamOpenCount.incrementAndGet();
                            return new ByteArrayInputStream("avatar".getBytes(StandardCharsets.UTF_8));
                        },
                        "image/png",
                        6,
                        "sha256-avatar"
                ),
                "creator-7"
        );
    }

    private static CompleteObjectUploadCommand completeCommand(
            ObjectUploadSessionResult prepared,
            AtomicInteger streamOpenCount
    ) {
        return new CompleteObjectUploadCommand(
                prepared.sessionId(),
                prepared.objectId(),
                prepared.versionId(),
                uploadContent(streamOpenCount),
                "user-7"
        );
    }

    private static ObjectUploadContent uploadContent(AtomicInteger streamOpenCount) {
        return new ObjectUploadContent(
                () -> {
                    streamOpenCount.incrementAndGet();
                    return new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8));
                },
                "text/plain",
                2,
                "sha256-note"
        );
    }

    private static void assertHiddenObjectNotFound(Throwable throwable) {
        assertThat(throwable).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND);
            assertThat(exception.getMessage()).isEqualTo("OSS object not found");
        });
    }

    private static void assertCompletedReplayHiddenWithoutSideEffects(CompletedReplayFixture fixture) {
        AtomicInteger streamOpenCount = new AtomicInteger();

        assertThatThrownBy(() -> fixture.service.completeUpload(
                completedReplayCommand(fixture, streamOpenCount)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND);
                    assertThat(exception.getMessage()).isEqualTo("OSS object not found");
                });

        assertCompletedReplayHasNoSideEffects(fixture, streamOpenCount);
    }

    private static void assertCompletedReplayHasNoSideEffects(
            CompletedReplayFixture fixture,
            AtomicInteger streamOpenCount
    ) {
        assertThat(streamOpenCount).hasValue(0);
        assertThat(fixture.objectRepository.saveCount).isZero();
        assertThat(fixture.versionRepository.saveCount).isZero();
        assertThat(fixture.sessionRepository.mutationCount).isZero();
        assertThat(fixture.objectStore.operationCount).isZero();
    }

    private record CompletedReplayFixture(
            UUID objectId,
            UUID versionId,
            UUID sessionId,
            FakeObjectRepository objectRepository,
            FakeObjectVersionRepository versionRepository,
            FakeUploadSessionRepository sessionRepository,
            CapturingObjectStore objectStore,
            ObjectUploadApplicationService service
    ) {
    }

    private static InternalUploadFixture internalUploadFixture(long requestSuffix) {
        FakeObjectRepository objectRepository = new FakeObjectRepository();
        FakeObjectVersionRepository versionRepository = new FakeObjectVersionRepository();
        FakeUploadSessionRepository sessionRepository = new FakeUploadSessionRepository();
        CapturingObjectStore objectStore = new CapturingObjectStore();
        ObjectUploadApplicationService service = new ObjectUploadApplicationService(
                objectRepository,
                versionRepository,
                sessionRepository,
                objectStore,
                "community-oss",
                "http://localhost:12880",
                CLOCK
        );
        ObjectUploadSessionResult prepared = service.prepareInternalUpload(
                "community-app",
                new PrepareObjectUploadCommand(
                        uuid(requestSuffix),
                        "DRIVE_FILE",
                        "community-app",
                        "drive",
                        "DRIVE_UPLOAD",
                        "upload-7",
                        "PRIVATE",
                        "note.txt",
                        "text/plain",
                        2,
                        "sha256-note",
                        "user-7"
                ));
        InternalUploadFixture fixture = new InternalUploadFixture(
                objectRepository,
                versionRepository,
                sessionRepository,
                objectStore,
                service,
                prepared
        );
        fixture.resetEffects();
        return fixture;
    }

    private static void assertInternalCompletionHiddenWithoutEffects(
            InternalUploadFixture fixture,
            Throwable failure,
            AtomicInteger streamOpenCount
    ) {
        assertAll(
                () -> assertHiddenObjectNotFound(failure),
                () -> assertThat(streamOpenCount).hasValue(0),
                () -> assertThat(fixture.objectRepository.saveCount).isZero(),
                () -> assertThat(fixture.versionRepository.saveCount).isZero(),
                () -> assertThat(fixture.sessionRepository.mutationCount).isZero(),
                () -> assertThat(fixture.objectStore.operationCount).isZero(),
                () -> assertThat(fixture.sessionRepository.findById(fixture.prepared.sessionId()).orElseThrow().status())
                        .isEqualTo(OssUploadSessionStatus.READY)
        );
    }

    private record InternalUploadFixture(
            FakeObjectRepository objectRepository,
            FakeObjectVersionRepository versionRepository,
            FakeUploadSessionRepository sessionRepository,
            CapturingObjectStore objectStore,
            ObjectUploadApplicationService service,
            ObjectUploadSessionResult prepared
    ) {
        private void resetEffects() {
            objectRepository.saveCount = 0;
            versionRepository.saveCount = 0;
            sessionRepository.mutationCount = 0;
            objectStore.operationCount = 0;
        }
    }

    private static ObjectUploadApplicationService serviceWithUploadPolicy(UploadPolicyProperties uploadPolicy) {
        return new ObjectUploadApplicationService(
                new FakeObjectRepository(),
                new FakeObjectVersionRepository(),
                new FakeUploadSessionRepository(),
                null,
                new CapturingObjectStore(),
                "community-oss",
                "http://localhost:12880",
                CLOCK,
                new UploadPolicyDecisions(uploadPolicy)
        );
    }

    private static final class FakeObjectRepository implements OssObjectRepository {
        private final Map<UUID, OssObject> rows = new HashMap<>();
        private int saveCount;

        @Override
        public void save(OssObject object) {
            saveCount++;
            rows.put(object.objectId(), object);
        }

        @Override
        public Optional<OssObject> findById(UUID objectId) {
            return Optional.ofNullable(rows.get(objectId));
        }
    }

    private static final class FakeObjectVersionRepository implements OssObjectVersionRepository {
        private final Map<UUID, OssObjectVersion> rows = new HashMap<>();
        private int saveCount;

        @Override
        public void save(OssObjectVersion version) {
            saveCount++;
            rows.put(version.versionId(), version);
        }

        @Override
        public Optional<OssObjectVersion> findById(UUID versionId) {
            return Optional.ofNullable(rows.get(versionId));
        }
    }

    private static final class FakeUploadSessionRepository implements OssUploadSessionRepository {
        private final Map<UUID, OssUploadSession> rows = new HashMap<>();
        private int mutationCount;

        @Override
        public boolean create(OssUploadSession session) {
            boolean created = rows.putIfAbsent(session.sessionId(), session) == null;
            if (created) {
                mutationCount++;
            }
            return created;
        }

        @Override
        public void save(OssUploadSession session) {
            mutationCount++;
            rows.put(session.sessionId(), session);
        }

        @Override
        public Optional<OssUploadSession> findById(UUID sessionId) {
            return Optional.ofNullable(rows.get(sessionId));
        }

        @Override
        public boolean recordCompletionFailure(
                UUID sessionId,
                long claimVersion,
                String lastError,
                Instant updatedAt
        ) {
            OssUploadSession current = rows.get(sessionId);
            if (!matchesClaim(current, claimVersion)) {
                return false;
            }
            mutationCount++;
            rows.put(sessionId, current.recordClaimError(updatedAt, lastError));
            return true;
        }

        @Override
        public boolean resetFailedClaim(
                UUID sessionId,
                long claimVersion,
                Instant updatedAt,
                Instant retryExpiresAt
        ) {
            OssUploadSession current = rows.get(sessionId);
            if (!matchesClaim(current, claimVersion)) {
                return false;
            }
            mutationCount++;
            rows.put(sessionId, current.resetFailedClaim(updatedAt, retryExpiresAt));
            return true;
        }

        @Override
        public boolean completeClaim(UUID sessionId, long claimVersion, Instant completedAt) {
            OssUploadSession current = rows.get(sessionId);
            if (!matchesClaim(current, claimVersion)) {
                return false;
            }
            mutationCount++;
            rows.put(sessionId, current.complete(completedAt));
            return true;
        }

        private static boolean matchesClaim(OssUploadSession session, long claimVersion) {
            return session != null
                    && session.status() == OssUploadSessionStatus.UPLOADING
                    && session.claimVersion() == claimVersion;
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
        private String capturedContentType;
        private long capturedContentLength;
        private int operationCount;

        @Override
        public void put(String bucket, String key, InputStream content, long contentLength, String contentType) {
            operationCount++;
            capturedBucket = bucket;
            capturedKey = key;
            capturedContentType = contentType;
            capturedContentLength = contentLength;
        }

        @Override
        public Optional<ObjectStoreObject> head(String bucket, String key) {
            operationCount++;
            if (!java.util.Objects.equals(capturedBucket, bucket)
                    || !java.util.Objects.equals(capturedKey, key)) {
                return Optional.empty();
            }
            return Optional.of(new ObjectStoreObject(
                    bucket,
                    key,
                    capturedContentType,
                    capturedContentLength,
                    "captured-etag",
                    CLOCK.instant()
            ));
        }

        @Override
        public StoredObject get(String bucket, String key) {
            operationCount++;
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public void delete(String bucket, String key) {
            operationCount++;
        }

        @Override
        public PresignedObjectUrl presignUpload(String bucket, String key, Duration ttl, String contentType) {
            operationCount++;
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public PresignedObjectUrl presignDownload(String bucket, String key, Duration ttl) {
            operationCount++;
            throw new UnsupportedOperationException("not needed");
        }
    }
}
