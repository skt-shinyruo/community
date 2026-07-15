package com.nowcoder.community.oss.application;

import com.nowcoder.community.oss.application.command.CompleteObjectUploadCommand;
import com.nowcoder.community.oss.application.command.ObjectUploadContent;
import com.nowcoder.community.oss.application.command.PrepareObjectUploadCommand;
import com.nowcoder.community.oss.application.result.ObjectMetadataResult;
import com.nowcoder.community.oss.application.result.ObjectUploadSessionResult;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectStatus;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssObjectVersionStatus;
import com.nowcoder.community.oss.domain.model.OssUploadSession;
import com.nowcoder.community.oss.domain.model.OssUploadSessionStatus;
import com.nowcoder.community.oss.domain.model.OssVisibility;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.domain.repository.OssUploadSessionRepository;
import com.nowcoder.community.oss.infrastructure.config.OssProperties;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStore;
import com.nowcoder.community.oss.infrastructure.storage.ObjectStoreObject;
import com.nowcoder.community.oss.infrastructure.storage.PresignedObjectUrl;
import com.nowcoder.community.oss.infrastructure.storage.StoredObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

class ObjectUploadReliabilityContractTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);
    private static final UUID REQUEST_ID = uuid(7201);

    @Test
    void prepareReplayMustReturnTheSameSessionAndPersistOneSemanticRequest() {
        ServiceHarness harness = new ServiceHarness();
        PrepareObjectUploadCommand command = prepareCommand(REQUEST_ID, "post.png");

        ObjectUploadSessionResult first = harness.service.prepareUpload(command);
        ObjectUploadSessionResult replay = harness.service.prepareUpload(command);

        assertThat(replay).isEqualTo(first);
        assertThat(harness.objectSaveCalls).hasValue(1);
        assertThat(harness.versionSaveCalls).hasValue(1);
        assertThat(harness.sessionSaveCalls).hasValue(1);
    }

    @Test
    void samePrepareRequestIdWithDifferentSemanticsMustConflictWithoutNewRows() {
        ServiceHarness harness = new ServiceHarness();
        harness.service.prepareUpload(prepareCommand(REQUEST_ID, "post.png"));

        assertThatThrownBy(() -> harness.service.prepareUpload(prepareCommand(REQUEST_ID, "different.png")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflict");
        assertThat(harness.objectSaveCalls).hasValue(1);
        assertThat(harness.versionSaveCalls).hasValue(1);
        assertThat(harness.sessionSaveCalls).hasValue(1);
    }

    @Test
    void expiredReadyPrepareReplayMustRenewTheSameSessionAndRemainCompletable() {
        ServiceHarness harness = new ServiceHarness();
        PrepareObjectUploadCommand command = prepareCommand(REQUEST_ID, "post.png");
        ObjectUploadSessionResult prepared = harness.service.prepareUpload(command);
        harness.session.set(copyWithExpiry(harness.session.get(), CLOCK.instant().minusSeconds(1)));

        ObjectUploadSessionResult renewed = harness.service.prepareUpload(command);

        assertThat(renewed.sessionId()).isEqualTo(prepared.sessionId());
        assertThat(renewed.objectId()).isEqualTo(prepared.objectId());
        assertThat(renewed.versionId()).isEqualTo(prepared.versionId());
        assertThat(renewed.expiresAt()).isAfter(CLOCK.instant());
        assertThat(harness.service.completeUpload(completeCommand(renewed, new AtomicInteger())).status())
                .isEqualTo(OssObjectStatus.ACTIVE.name());
    }

    @Test
    void concurrentPrepareWithDifferentSemanticsMustNotMutateTheWinningRequest() throws Exception {
        ServiceHarness harness = new ServiceHarness();
        harness.gateTwoInitialPrepareLookups.set(true);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ObjectUploadSessionResult> first = executor.submit(() ->
                    harness.service.prepareUpload(prepareCommand(REQUEST_ID, "first.png")));
            Future<ObjectUploadSessionResult> second = executor.submit(() ->
                    harness.service.prepareUpload(prepareCommand(REQUEST_ID, "second.png")));
            assertThat(harness.initialPrepareLookups.await(1, TimeUnit.SECONDS)).isTrue();
            harness.releasePrepareLookups.countDown();

            int successes = 0;
            int conflicts = 0;
            for (Future<ObjectUploadSessionResult> result : List.of(first, second)) {
                try {
                    result.get(2, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException failure) {
                    assertThat(failure.getCause())
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("conflict");
                    conflicts++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(harness.objectSaveCalls).hasValue(1);
            assertThat(harness.versionSaveCalls).hasValue(1);
            assertThat(harness.sessionSaveCalls).hasValue(1);
            assertThat(harness.object.get().objectId())
                    .isEqualTo(harness.session.get().objectId());
            assertThat(harness.object.get().ownerId())
                    .isEqualTo(harness.session.get().ownerId());
            assertThat(harness.version.get().fileName())
                    .isEqualTo(harness.session.get().expectedFileName());
        } finally {
            harness.releasePrepareLookups.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void completedSessionReplayMustReturnEveryCanonicalMetadataFieldWithoutSecondPut() {
        ServiceHarness harness = new ServiceHarness();
        ObjectUploadSessionResult prepared = harness.prepareLegacy();
        AtomicInteger streamOpenCalls = new AtomicInteger();
        CompleteObjectUploadCommand command = completeCommand(prepared, streamOpenCalls);

        ObjectMetadataResult first = harness.service.completeUpload(command);
        ObjectMetadataResult replay = harness.service.completeUpload(command);

        assertThat(replay).isEqualTo(first);
        assertThat(replay.objectId()).isEqualTo(prepared.objectId());
        assertThat(replay.currentVersionId()).isEqualTo(prepared.versionId());
        assertThat(replay.usage()).isEqualTo("CONTENT_POST_MEDIA");
        assertThat(replay.ownerService()).isEqualTo("community-app");
        assertThat(replay.ownerDomain()).isEqualTo("content");
        assertThat(replay.ownerType()).isEqualTo("post-media-draft");
        assertThat(replay.ownerId()).isEqualTo("asset-7");
        assertThat(replay.visibility()).isEqualTo("PUBLIC");
        assertThat(replay.status()).isEqualTo("ACTIVE");
        assertThat(replay.fileName()).isEqualTo("post.png");
        assertThat(replay.contentType()).isEqualTo("image/png");
        assertThat(replay.contentLength()).isEqualTo(4L);
        assertThat(replay.checksumSha256()).isEqualTo("sha256-post");
        assertThat(replay.publicUrl()).contains(prepared.objectId().toString(), prepared.versionId().toString());
        assertThat(harness.objectStore.putCalls).hasValue(1);
        assertThat(streamOpenCalls).hasValue(1);
    }

    @Test
    void concurrentCompleteMustAllowOnlyOneObjectStoreClaimant() throws Exception {
        ServiceHarness harness = new ServiceHarness();
        ObjectUploadSessionResult prepared = harness.prepareLegacy();
        harness.objectStore.blockFirstPut.set(true);
        CompleteObjectUploadCommand command = completeCommand(prepared, new AtomicInteger());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ObjectMetadataResult> first = executor.submit(() -> harness.service.completeUpload(command));
            assertThat(harness.objectStore.firstPutEntered.await(1, TimeUnit.SECONDS)).isTrue();
            CountDownLatch secondStarted = new CountDownLatch(1);
            Future<ObjectMetadataResult> second = executor.submit(() -> {
                secondStarted.countDown();
                return harness.service.completeUpload(command);
            });
            assertThat(secondStarted.await(1, TimeUnit.SECONDS)).isTrue();

            boolean duplicatePutObserved = harness.objectStore.duplicatePutEntered.await(300, TimeUnit.MILLISECONDS);
            harness.objectStore.releaseFirstPut.countDown();
            first.get(2, TimeUnit.SECONDS);
            awaitAllowingStableInProgressFailure(second);

            assertThat(duplicatePutObserved)
                    .as("a concurrent replay must not become a second ObjectStore writer")
                    .isFalse();
            assertThat(harness.objectStore.putCalls).hasValue(1);
        } finally {
            harness.objectStore.releaseFirstPut.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void staleWriterReleasedAfterResetAndRetryMustNotOverwriteTheWinningAttempt() throws Exception {
        ServiceHarness harness = new ServiceHarness();
        ObjectUploadSessionResult prepared = harness.prepareLegacy();
        harness.objectStore.blockFirstPut.set(true);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ObjectMetadataResult> staleWriter = executor.submit(() -> harness.service.completeUpload(
                    completeCommand(prepared, new AtomicInteger(), "old!")));
            assertThat(harness.objectStore.firstPutEntered.await(1, TimeUnit.SECONDS)).isTrue();

            harness.recover(CLOCK.instant().plusSeconds(60));
            assertThat(harness.session.get().status()).isEqualTo(OssUploadSessionStatus.READY);

            ObjectMetadataResult winner = harness.service.completeUpload(
                    completeCommand(prepared, new AtomicInteger(), "new!"));
            String winningKey = harness.version.get().storageKey();
            assertThat(winner.status()).isEqualTo(OssObjectStatus.ACTIVE.name());

            harness.objectStore.releaseFirstPut.countDown();
            assertThatThrownBy(() -> staleWriter.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("claim");

            assertThat(harness.objectStore.putKeys).hasSize(2);
            assertThat(winningKey).isEqualTo(harness.objectStore.putKeys.get(1));
            assertThat(harness.version.get().storageKey()).isEqualTo(winningKey);
            assertThat(harness.version.get().etag()).isEqualTo("etag-put-2");
            StoredObject canonical = harness.objectStore.get("community-oss", winningKey);
            assertThat(new String(canonical.content().readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("new!");
        } finally {
            harness.objectStore.releaseFirstPut.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void putSuccessAndDbFinalizeFailureMustLeaveDurableUploadingClaim() {
        ServiceHarness harness = new ServiceHarness();
        ObjectUploadSessionResult prepared = harness.prepareLegacy();
        harness.failNextActiveVersionSave.set(true);

        Throwable failure = catchThrowable(() ->
                harness.service.completeUpload(completeCommand(prepared, new AtomicInteger())));

        assertThat(failure).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("finalize");
        assertThat(harness.objectStore.putCalls).hasValue(1);
        assertThat(harness.objectStore.head("community-oss", harness.objectStore.putKeys.get(0))).isPresent();
        assertThat(harness.session.get().status()).isEqualTo(OssUploadSessionStatus.UPLOADING);
        assertThat(harness.objectStore.deleteCalls).hasValue(0);
    }

    @Test
    void storedObjectWithFailedFinalizeMustBeScannedAndRecoveredWithoutAnotherPut() throws Exception {
        ServiceHarness harness = new ServiceHarness();
        harness.seedRecoverableStoredObject();
        Class<?> recoveryType = Class.forName(
                "com.nowcoder.community.oss.application.ObjectUploadRecoveryApplicationService");
        Object recovery = instantiateRecovery(recoveryType, harness);
        Method recover = recoveryType.getMethod("recoverStaleUploads", Instant.class, int.class);

        recover.invoke(recovery, CLOCK.instant().plusSeconds(60), 10);

        assertThat(harness.session.get().status()).isEqualTo(OssUploadSessionStatus.COMPLETED);
        assertThat(harness.object.get().status()).isEqualTo(OssObjectStatus.ACTIVE);
        assertThat(harness.version.get().status()).isEqualTo(OssObjectVersionStatus.ACTIVE);
        assertThat(harness.objectStore.putCalls).hasValue(1);
        assertThat(harness.objectStore.deleteCalls).hasValue(0);
    }

    @Test
    void recoveryMustRejectStoredMetadataThatDoesNotMatchTheUploadClaim() throws Exception {
        ServiceHarness harness = new ServiceHarness();
        harness.seedRecoverableStoredObject();
        OssUploadSession uploading = harness.session.get();
        OssObjectVersion stagedVersion = harness.version.get();
        harness.objectStore.seedStored(
                stagedVersion.storageBucket(),
                ObjectUploadApplicationService.attemptStorageKey(uploading),
                "image/jpeg",
                uploading.expectedContentLength() + 1L
        );

        harness.recover(CLOCK.instant().plusSeconds(60));

        assertThat(harness.session.get().status()).isEqualTo(OssUploadSessionStatus.UPLOADING);
        assertThat(harness.session.get().lastError())
                .startsWith("RECOVERY_FAILED:")
                .contains("does not match");
        assertThat(harness.session.get().updatedAt()).isEqualTo(CLOCK.instant());
        assertThat(harness.object.get().status()).isEqualTo(OssObjectStatus.STAGED);
        assertThat(harness.version.get().status()).isEqualTo(OssObjectVersionStatus.STAGED);
    }

    @Test
    void recoveryMustPreserveWildcardUploadClaimSemanticsAfterPut() throws Exception {
        ServiceHarness harness = new ServiceHarness();
        harness.seedRecoverableStoredObject();
        OssUploadSession wildcardClaim = copyWithExpectedMetadata(
                harness.session.get(), "application/octet-stream", 0L);
        harness.session.set(wildcardClaim);
        OssObjectVersion stagedVersion = harness.version.get();
        harness.objectStore.seedStored(
                stagedVersion.storageBucket(),
                ObjectUploadApplicationService.attemptStorageKey(wildcardClaim),
                "image/jpeg",
                9L
        );

        harness.recover(CLOCK.instant().plusSeconds(60));

        assertThat(harness.session.get().status()).isEqualTo(OssUploadSessionStatus.COMPLETED);
        assertThat(harness.version.get().status()).isEqualTo(OssObjectVersionStatus.ACTIVE);
        assertThat(harness.version.get().contentType()).isEqualTo("image/jpeg");
        assertThat(harness.version.get().contentLength()).isEqualTo(9L);
    }

    @Test
    void directCompletionMustRejectHeadMetadataThatDiffersFromSubmittedContentForWildcardClaim() {
        ServiceHarness harness = new ServiceHarness();
        ObjectUploadSessionResult prepared = harness.prepareLegacy();
        harness.session.set(copyWithExpectedMetadata(
                harness.session.get(), "application/octet-stream", 0L));
        harness.objectStore.overrideNextHeadMetadata("image/jpeg", 9L);

        assertThatThrownBy(() -> harness.service.completeUpload(
                completeCommand(prepared, new AtomicInteger())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("submitted content");

        assertThat(harness.session.get().status()).isEqualTo(OssUploadSessionStatus.UPLOADING);
        assertThat(harness.version.get().status()).isEqualTo(OssObjectVersionStatus.STAGED);
        assertThat(harness.object.get().status()).isEqualTo(OssObjectStatus.STAGED);
    }

    @Test
    void failedPutWithConfirmedMissingObjectMustResetTheFencedClaimAndAllowRetry() throws Exception {
        ServiceHarness harness = new ServiceHarness();
        ObjectUploadSessionResult prepared = harness.prepareLegacy();
        harness.objectStore.failNextPut.set(true);
        harness.objectStore.completeFailedPutAfterRetry.set(true);

        assertThatThrownBy(() -> harness.service.completeUpload(
                completeCommand(prepared, new AtomicInteger())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed to store object content");

        OssUploadSession failedClaim = harness.session.get();
        assertThat(failedClaim.status()).isEqualTo(OssUploadSessionStatus.UPLOADING);
        assertThat(failedClaim.claimVersion()).isPositive();
        assertThat(failedClaim.lastError()).startsWith("PUT_FAILED:");
        assertThat(harness.objectStore.head("community-oss", harness.objectStore.putKeys.get(0))).isEmpty();

        harness.recover(CLOCK.instant().plusSeconds(60));

        OssUploadSession reset = harness.session.get();
        assertThat(reset.status()).isEqualTo(OssUploadSessionStatus.READY);
        assertThat(reset.claimVersion()).isGreaterThan(failedClaim.claimVersion());
        assertThat(reset.lastError()).isEmpty();

        ObjectMetadataResult retried = harness.service.completeUpload(
                completeCommand(prepared, new AtomicInteger()));
        harness.objectStore.releaseDelayedFailedPut.countDown();
        assertThat(harness.objectStore.delayedFailedPutCompleted.await(1, TimeUnit.SECONDS)).isTrue();

        assertThat(retried.status()).isEqualTo(OssObjectStatus.ACTIVE.name());
        assertThat(harness.session.get().status()).isEqualTo(OssUploadSessionStatus.COMPLETED);
        assertThat(harness.session.get().claimVersion()).isGreaterThan(reset.claimVersion());
        assertThat(harness.objectStore.putCalls).hasValue(2);
        assertThat(harness.objectStore.putKeys).hasSize(2);
        assertThat(harness.objectStore.putKeys.get(0)).isNotEqualTo(harness.objectStore.putKeys.get(1));
        assertThat(harness.version.get().storageKey()).isEqualTo(harness.objectStore.putKeys.get(1));
        assertThat(harness.version.get().etag()).isEqualTo("etag-put-2");
        assertThat(harness.objectStore.head("community-oss", harness.objectStore.putKeys.get(0))).isPresent();
        assertThat(harness.objectStore.head("community-oss", harness.objectStore.putKeys.get(1))).isPresent();
    }

    @Test
    void resetClaimMustFenceARecoveryFinalizeThatLoadedTheOldClaim() {
        ServiceHarness harness = new ServiceHarness();
        ObjectUploadSessionResult prepared = harness.prepareLegacy();
        Instant now = CLOCK.instant();
        assertThat(harness.sessionRepository.claimForCompletion(prepared.sessionId(), now)).isTrue();
        OssUploadSession oldClaim = harness.session.get();
        assertThat(harness.sessionRepository.recordCompletionFailure(
                oldClaim.sessionId(), oldClaim.claimVersion(), "PUT_FAILED:timeout", now.plusSeconds(1))).isTrue();
        assertThat(harness.sessionRepository.resetFailedClaim(
                oldClaim.sessionId(),
                oldClaim.claimVersion(),
                now.plusSeconds(2),
                now.plusSeconds(902))).isTrue();

        ObjectUploadTransactionOperations operations = new ObjectUploadTransactionOperations(
                harness.objectRepository, harness.versionRepository, harness.sessionRepository);
        OssObjectVersion activatedVersion = harness.version.get().activate("late-etag", now.plusSeconds(3));
        OssObject activatedObject = harness.object.get().activate(activatedVersion, now.plusSeconds(3));
        OssObject originalObject = harness.object.get();
        OssObjectVersion originalVersion = harness.version.get();

        assertThatThrownBy(() -> operations.finalizeUpload(
                activatedVersion, activatedObject, oldClaim.complete(now.plusSeconds(3))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("claim");
        assertThat(harness.session.get().status()).isEqualTo(OssUploadSessionStatus.READY);
        assertThat(harness.session.get().claimVersion()).isGreaterThan(oldClaim.claimVersion());
        assertThat(harness.object.get()).isSameAs(originalObject);
        assertThat(harness.version.get()).isSameAs(originalVersion);
    }

    @Test
    void recoveryMustIsolateOneHeadFailureAndContinueWithTheNextCandidate() throws Exception {
        ServiceHarness harness = new ServiceHarness();
        harness.seedRecoverableStoredObject();
        harness.objectStore.failNextHead.set(true);
        harness.recoveryCopies.set(2);

        harness.recover(CLOCK.instant().plusSeconds(60));

        assertThat(harness.objectStore.headCalls).hasValue(2);
        assertThat(harness.session.get().status()).isEqualTo(OssUploadSessionStatus.COMPLETED);
        assertThat(harness.object.get().status()).isEqualTo(OssObjectStatus.ACTIVE);
    }

    @Test
    void failedOldestCandidateMustAdvanceItsObservationTimeAndNotStarvePastTheLimit() {
        RecoveryFixture bad = recoveryFixture(7300, CLOCK.instant().minusSeconds(120));
        RecoveryFixture good = recoveryFixture(7400, CLOCK.instant().minusSeconds(60));
        Map<UUID, OssObject> objects = new ConcurrentHashMap<>(Map.of(
                bad.object().objectId(), bad.object(),
                good.object().objectId(), good.object()
        ));
        Map<UUID, OssObjectVersion> versions = new ConcurrentHashMap<>(Map.of(
                bad.version().versionId(), bad.version(),
                good.version().versionId(), good.version()
        ));
        Map<UUID, OssUploadSession> sessions = new ConcurrentHashMap<>(Map.of(
                bad.session().sessionId(), bad.session(),
                good.session().sessionId(), good.session()
        ));
        OssObjectRepository objectRepository = mock(OssObjectRepository.class, invocation -> {
            if (invocation.getMethod().getName().equals("findById")) {
                return Optional.ofNullable(objects.get(invocation.getArgument(0)));
            }
            if (invocation.getMethod().getName().equals("save")) {
                OssObject value = invocation.getArgument(0);
                objects.put(value.objectId(), value);
                return null;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        OssObjectVersionRepository versionRepository = mock(OssObjectVersionRepository.class, invocation -> {
            if (invocation.getMethod().getName().equals("findById")) {
                return Optional.ofNullable(versions.get(invocation.getArgument(0)));
            }
            if (invocation.getMethod().getName().equals("save")) {
                OssObjectVersion value = invocation.getArgument(0);
                versions.put(value.versionId(), value);
                return null;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        OssUploadSessionRepository sessionRepository = mock(OssUploadSessionRepository.class, invocation -> {
            String name = invocation.getMethod().getName();
            if (name.equals("listRecoverable")) {
                Instant cutoff = invocation.getArgument(0);
                int limit = invocation.getArgument(1);
                return sessions.values().stream()
                        .filter(value -> value.status() == OssUploadSessionStatus.UPLOADING)
                        .filter(value -> !value.updatedAt().isAfter(cutoff))
                        .sorted(java.util.Comparator.comparing(OssUploadSession::updatedAt))
                        .limit(limit)
                        .toList();
            }
            if (name.equals("findById")) {
                return Optional.ofNullable(sessions.get(invocation.getArgument(0)));
            }
            if (name.equals("recordCompletionFailure")) {
                UUID id = invocation.getArgument(0);
                long claimVersion = invocation.getArgument(1);
                OssUploadSession current = sessions.get(id);
                if (!ServiceHarness.matchesClaim(current, claimVersion)) {
                    return false;
                }
                sessions.put(id, current.recordClaimError(
                        invocation.getArgument(3), invocation.getArgument(2)));
                return true;
            }
            if (name.equals("completeClaim")) {
                UUID id = invocation.getArgument(0);
                long claimVersion = invocation.getArgument(1);
                OssUploadSession current = sessions.get(id);
                if (!ServiceHarness.matchesClaim(current, claimVersion)) {
                    return false;
                }
                sessions.put(id, current.complete(invocation.getArgument(2)));
                return true;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        CountingObjectStore objectStore = new CountingObjectStore();
        objectStore.failNextHead.set(true);
        objectStore.seedStored(
                good.version().storageBucket(),
                ObjectUploadApplicationService.attemptStorageKey(good.session()),
                good.version().contentType(),
                good.version().contentLength()
        );
        ObjectUploadRecoveryApplicationService recovery = new ObjectUploadRecoveryApplicationService(
                objectRepository, versionRepository, sessionRepository, objectStore, CLOCK);
        Instant cutoff = CLOCK.instant().minusSeconds(30);

        recovery.recoverStaleUploads(cutoff, 1);

        OssUploadSession observedBad = sessions.get(bad.session().sessionId());
        assertThat(observedBad.lastError()).startsWith("RECOVERY_FAILED:");
        assertThat(observedBad.updatedAt()).isEqualTo(CLOCK.instant());

        recovery.recoverStaleUploads(cutoff, 1);

        assertThat(sessions.get(good.session().sessionId()).status())
                .isEqualTo(OssUploadSessionStatus.COMPLETED);
        assertThat(sessions.get(bad.session().sessionId()).status())
                .isEqualTo(OssUploadSessionStatus.UPLOADING);
    }

    @Test
    void claimThatExpiredBeforePutMustResetWithoutFailureEvidenceAndConvergeOnRetry() throws Exception {
        ServiceHarness harness = new ServiceHarness();
        ObjectUploadSessionResult prepared = harness.prepareLegacy();
        OssUploadSession ready = harness.session.get();
        harness.session.set(copyWithExpiry(ready, CLOCK.instant().minusSeconds(1)));
        assertThat(harness.sessionRepository.claimForCompletion(
                prepared.sessionId(), CLOCK.instant().minusSeconds(2))).isTrue();
        OssUploadSession abandoned = harness.session.get();
        assertThat(abandoned.status()).isEqualTo(OssUploadSessionStatus.UPLOADING);
        assertThat(abandoned.lastError()).isEmpty();

        harness.recover(CLOCK.instant().plusSeconds(60));

        OssUploadSession reset = harness.session.get();
        assertThat(reset.status()).isEqualTo(OssUploadSessionStatus.READY);
        assertThat(reset.claimVersion()).isGreaterThan(abandoned.claimVersion());
        assertThat(reset.expiresAt()).isAfter(CLOCK.instant());
        ObjectMetadataResult completed = harness.service.completeUpload(
                completeCommand(prepared, new AtomicInteger()));
        assertThat(completed.status()).isEqualTo(OssObjectStatus.ACTIVE.name());
        assertThat(harness.session.get().status()).isEqualTo(OssUploadSessionStatus.COMPLETED);
    }

    @Test
    void sessionRepositoryMustExposeVersionedClaimRecoveryAndFinalizeCas() throws Exception {
        Method claim = OssUploadSessionRepository.class.getMethod(
                "claimForCompletion", UUID.class, Instant.class);
        Method recordFailure = OssUploadSessionRepository.class.getMethod(
                "recordCompletionFailure", UUID.class, long.class, String.class, Instant.class);
        Method reset = OssUploadSessionRepository.class.getMethod(
                "resetFailedClaim", UUID.class, long.class, Instant.class, Instant.class);
        Method complete = OssUploadSessionRepository.class.getMethod(
                "completeClaim", UUID.class, long.class, Instant.class);
        Method scan = OssUploadSessionRepository.class.getMethod(
                "listRecoverable", Instant.class, int.class);

        assertThat(claim.getReturnType()).isEqualTo(boolean.class);
        assertThat(recordFailure.getReturnType()).isEqualTo(boolean.class);
        assertThat(reset.getReturnType()).isEqualTo(boolean.class);
        assertThat(complete.getReturnType()).isEqualTo(boolean.class);
        assertThat(scan.getReturnType()).isEqualTo(List.class);
    }

    private static PrepareObjectUploadCommand prepareCommand(UUID requestId, String fileName) {
        RecordComponent[] components = PrepareObjectUploadCommand.class.getRecordComponents();
        assertThat(Arrays.stream(components).map(RecordComponent::getName))
                .as("OSS prepare needs a caller-supplied deterministic requestId")
                .contains("requestId");
        Object[] arguments = Arrays.stream(components)
                .map(component -> prepareValue(component.getName(), requestId, fileName))
                .toArray();
        try {
            Constructor<PrepareObjectUploadCommand> constructor = PrepareObjectUploadCommand.class
                    .getDeclaredConstructor(Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new));
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("cannot create deterministic OSS prepare command", error);
        }
    }

    private static Object prepareValue(String name, UUID requestId, String fileName) {
        return switch (name) {
            case "requestId" -> requestId;
            case "usage" -> "CONTENT_POST_MEDIA";
            case "ownerService" -> "community-app";
            case "ownerDomain" -> "content";
            case "ownerType" -> "post-media-draft";
            case "ownerId" -> "asset-7";
            case "visibility" -> "PUBLIC";
            case "fileName" -> fileName;
            case "contentType" -> "image/png";
            case "contentLength" -> 4L;
            case "checksumSha256" -> "sha256-post";
            case "actorId" -> "actor-7";
            default -> throw new AssertionError("unexpected prepare command component: " + name);
        };
    }

    private static CompleteObjectUploadCommand completeCommand(
            ObjectUploadSessionResult prepared,
            AtomicInteger streamOpenCalls
    ) {
        return completeCommand(prepared, streamOpenCalls, "post");
    }

    private static CompleteObjectUploadCommand completeCommand(
            ObjectUploadSessionResult prepared,
            AtomicInteger streamOpenCalls,
            String payload
    ) {
        return new CompleteObjectUploadCommand(
                prepared.sessionId(),
                prepared.objectId(),
                prepared.versionId(),
                new ObjectUploadContent(
                        () -> {
                            streamOpenCalls.incrementAndGet();
                            return new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
                        },
                        "image/png",
                        4L,
                        "sha256-post"
                )
        );
    }

    private static OssUploadSession copyWithExpiry(OssUploadSession session, Instant expiresAt) {
        return new OssUploadSession(
                session.sessionId(),
                session.requestId(),
                session.objectId(),
                session.versionId(),
                session.uploadMode(),
                session.ownerService(),
                session.ownerDomain(),
                session.ownerType(),
                session.ownerId(),
                session.expectedFileName(),
                session.expectedContentType(),
                session.expectedContentLength(),
                session.expectedChecksumSha256(),
                session.status(),
                session.claimVersion(),
                expiresAt,
                session.createdBy(),
                session.createdAt(),
                session.updatedAt(),
                session.completedAt(),
                session.lastError()
        );
    }

    private static OssUploadSession copyWithExpectedMetadata(
            OssUploadSession session,
            String expectedContentType,
            long expectedContentLength
    ) {
        return new OssUploadSession(
                session.sessionId(),
                session.requestId(),
                session.objectId(),
                session.versionId(),
                session.uploadMode(),
                session.ownerService(),
                session.ownerDomain(),
                session.ownerType(),
                session.ownerId(),
                session.expectedFileName(),
                expectedContentType,
                expectedContentLength,
                session.expectedChecksumSha256(),
                session.status(),
                session.claimVersion(),
                session.expiresAt(),
                session.createdBy(),
                session.createdAt(),
                session.updatedAt(),
                session.completedAt(),
                session.lastError()
        );
    }

    private static RecoveryFixture recoveryFixture(long suffix, Instant claimedAt) {
        UUID objectId = uuid(suffix + 1);
        UUID versionId = uuid(suffix + 2);
        OssObject object = OssObject.stage(
                objectId,
                "CONTENT_POST_MEDIA",
                "community-app",
                "content",
                "post-media-draft",
                "asset-" + suffix,
                OssVisibility.PUBLIC,
                "actor-" + suffix,
                claimedAt.minusSeconds(60)
        );
        OssObjectVersion version = OssObjectVersion.staged(
                versionId,
                objectId,
                "S3_COMPATIBLE",
                "community-oss",
                "objects/" + objectId + "/" + versionId + "/post.png",
                "post.png",
                "image/png",
                4L,
                "sha256-post",
                claimedAt.minusSeconds(60)
        );
        OssUploadSession session = OssUploadSession.ready(
                uuid(suffix + 3),
                objectId,
                versionId,
                "PROXY",
                "community-app",
                "content",
                "post-media-draft",
                "asset-" + suffix,
                "post.png",
                "image/png",
                4L,
                "sha256-post",
                "actor-" + suffix,
                claimedAt.minusSeconds(60),
                claimedAt.plusSeconds(900)
        ).startUploading(claimedAt);
        return new RecoveryFixture(object, version, session);
    }

    private record RecoveryFixture(
            OssObject object,
            OssObjectVersion version,
            OssUploadSession session
    ) {
    }

    private static void awaitAllowingStableInProgressFailure(Future<ObjectMetadataResult> future) throws Exception {
        try {
            future.get(2, TimeUnit.SECONDS);
        } catch (ExecutionException error) {
            assertThat(error.getCause()).isInstanceOf(RuntimeException.class);
        }
    }

    private static Object instantiateRecovery(Class<?> recoveryType, ServiceHarness harness) throws Exception {
        Constructor<?> constructor = Arrays.stream(recoveryType.getDeclaredConstructors())
                .min(java.util.Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow();
        Object[] arguments = Arrays.stream(constructor.getParameterTypes()).map(type -> {
            if (type == OssObjectRepository.class) {
                return harness.objectRepository;
            }
            if (type == OssObjectVersionRepository.class) {
                return harness.versionRepository;
            }
            if (type == OssUploadSessionRepository.class) {
                return harness.sessionRepository;
            }
            if (type == ObjectStore.class) {
                return harness.objectStore;
            }
            if (type == Clock.class) {
                return CLOCK;
            }
            if (type == OssProperties.class) {
                return new OssProperties();
            }
            if (type == String.class) {
                return "community-oss";
            }
            if (type.isInterface()) {
                return mock(type);
            }
            throw new AssertionError("unsupported recovery dependency: " + type.getName());
        }).toArray();
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static final class ServiceHarness {
        private final AtomicReference<OssObject> object = new AtomicReference<>();
        private final AtomicReference<OssObjectVersion> version = new AtomicReference<>();
        private final AtomicReference<OssUploadSession> session = new AtomicReference<>();
        private final AtomicReference<OssUploadSession> pendingCompletion = new AtomicReference<>();
        private final AtomicInteger objectSaveCalls = new AtomicInteger();
        private final AtomicInteger versionSaveCalls = new AtomicInteger();
        private final AtomicInteger sessionSaveCalls = new AtomicInteger();
        private final AtomicBoolean failNextActiveVersionSave = new AtomicBoolean();
        private final AtomicInteger recoveryCopies = new AtomicInteger(1);
        private final AtomicBoolean gateTwoInitialPrepareLookups = new AtomicBoolean();
        private final CountDownLatch initialPrepareLookups = new CountDownLatch(2);
        private final CountDownLatch releasePrepareLookups = new CountDownLatch(1);
        private final CountDownLatch preparedRowsCommitted = new CountDownLatch(1);
        private final OssObjectRepository objectRepository = mock(OssObjectRepository.class, invocation -> {
            String name = invocation.getMethod().getName();
            if (name.equals("create")) {
                if (object.get() != null) {
                    return false;
                }
                object.set(invocation.getArgument(0));
                objectSaveCalls.incrementAndGet();
                return true;
            }
            if (name.equals("save")) {
                object.set(invocation.getArgument(0));
                objectSaveCalls.incrementAndGet();
                OssUploadSession completed = pendingCompletion.getAndSet(null);
                if (completed != null) {
                    session.set(completed);
                }
                return null;
            }
            if (name.equals("findById")) {
                return Optional.ofNullable(object.get());
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        private final OssObjectVersionRepository versionRepository = mock(OssObjectVersionRepository.class, invocation -> {
            String name = invocation.getMethod().getName();
            if (name.equals("create")) {
                if (version.get() != null) {
                    return false;
                }
                version.set(invocation.getArgument(0));
                versionSaveCalls.incrementAndGet();
                preparedRowsCommitted.countDown();
                return true;
            }
            if (name.equals("save")) {
                OssObjectVersion candidate = invocation.getArgument(0);
                if (candidate.status() == OssObjectVersionStatus.ACTIVE
                        && failNextActiveVersionSave.compareAndSet(true, false)) {
                    throw new IllegalStateException("OSS DB finalize failed after put");
                }
                version.set(candidate);
                versionSaveCalls.incrementAndGet();
                return null;
            }
            if (name.equals("findById")) {
                return Optional.ofNullable(version.get());
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        private final OssUploadSessionRepository sessionRepository = mock(OssUploadSessionRepository.class, invocation -> {
            String name = invocation.getMethod().getName();
            if (name.equals("create")) {
                if (gateTwoInitialPrepareLookups.get()) {
                    initialPrepareLookups.countDown();
                    CountingObjectStore.await(releasePrepareLookups);
                }
                synchronized (session) {
                    if (session.get() != null) {
                        CountingObjectStore.await(preparedRowsCommitted);
                        return false;
                    }
                    session.set(invocation.getArgument(0));
                    sessionSaveCalls.incrementAndGet();
                    return true;
                }
            }
            if (name.equals("save")) {
                session.set(invocation.getArgument(0));
                sessionSaveCalls.incrementAndGet();
                return null;
            }
            if (name.equals("findById") || name.equals("findByRequestId")) {
                return Optional.ofNullable(session.get());
            }
            if (name.equals("claimForCompletion")) {
                synchronized (session) {
                    OssUploadSession current = session.get();
                    Instant updatedAt = invocation.getArgument(1);
                    if (current == null
                            || current.status() != OssUploadSessionStatus.READY
                            || current.expiredAt(updatedAt)) {
                        return false;
                    }
                    session.set(current.startUploading(updatedAt));
                    return true;
                }
            }
            if (name.equals("recordCompletionFailure")) {
                synchronized (session) {
                    OssUploadSession current = session.get();
                    long claimVersion = invocation.getArgument(1);
                    if (!matchesClaim(current, claimVersion)) {
                        return false;
                    }
                    session.set(current.recordClaimError(
                            invocation.getArgument(3), invocation.getArgument(2)));
                    return true;
                }
            }
            if (name.equals("resetFailedClaim")) {
                synchronized (session) {
                    OssUploadSession current = session.get();
                    long claimVersion = invocation.getArgument(1);
                    if (!matchesClaim(current, claimVersion)) {
                        return false;
                    }
                    Instant updatedAt = invocation.getArgument(2);
                    Instant retryExpiresAt = invocation.getArguments().length > 3
                            ? invocation.getArgument(3)
                            : updatedAt.plusSeconds(900);
                    session.set(current.resetFailedClaim(updatedAt, retryExpiresAt));
                    return true;
                }
            }
            if (name.equals("completeClaim")) {
                synchronized (session) {
                    OssUploadSession current = session.get();
                    long claimVersion = invocation.getArgument(1);
                    if (!matchesClaim(current, claimVersion)) {
                        return false;
                    }
                    pendingCompletion.set(current.complete(invocation.getArgument(2)));
                    return true;
                }
            }
            if (name.equals("renewReadySession")) {
                synchronized (session) {
                    OssUploadSession current = session.get();
                    Instant expectedExpiresAt = invocation.getArgument(1);
                    if (current == null
                            || current.status() != OssUploadSessionStatus.READY
                            || !current.expiresAt().equals(expectedExpiresAt)) {
                        return false;
                    }
                    session.set(current.renewReady(
                            invocation.getArgument(3), invocation.getArgument(2)));
                    return true;
                }
            }
            if (name.equals("listRecoverable")) {
                OssUploadSession current = session.get();
                return current != null && current.status() == OssUploadSessionStatus.UPLOADING
                        ? java.util.Collections.nCopies(recoveryCopies.get(), current)
                        : List.of();
            }
            if (invocation.getMethod().getReturnType() == boolean.class) {
                return true;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        private final CountingObjectStore objectStore = new CountingObjectStore();
        private final ObjectUploadApplicationService service = new ObjectUploadApplicationService(
                objectRepository,
                versionRepository,
                sessionRepository,
                objectStore,
                "community-oss",
                "https://cdn.example.test",
                CLOCK
        );

        private ObjectUploadSessionResult prepareLegacy() {
            return service.prepareUpload(new PrepareObjectUploadCommand(
                    "CONTENT_POST_MEDIA",
                    "community-app",
                    "content",
                    "post-media-draft",
                    "asset-7",
                    "PUBLIC",
                    "post.png",
                    "image/png",
                    4L,
                    "sha256-post",
                    "actor-7"
            ));
        }

        private void recover(Instant updatedBefore) throws Exception {
            Class<?> recoveryType = Class.forName(
                    "com.nowcoder.community.oss.application.ObjectUploadRecoveryApplicationService");
            Object recovery = instantiateRecovery(recoveryType, this);
            Method recover = recoveryType.getMethod("recoverStaleUploads", Instant.class, int.class);
            recover.invoke(recovery, updatedBefore, 10);
        }

        private void seedRecoverableStoredObject() {
            Instant now = CLOCK.instant();
            UUID objectId = uuid(7211);
            UUID versionId = uuid(7212);
            OssObject stagedObject = OssObject.stage(
                    objectId,
                    "CONTENT_POST_MEDIA",
                    "community-app",
                    "content",
                    "post-media-draft",
                    "asset-7",
                    OssVisibility.PUBLIC,
                    "actor-7",
                    now
            );
            OssObjectVersion stagedVersion = OssObjectVersion.staged(
                    versionId,
                    objectId,
                    "S3_COMPATIBLE",
                    "community-oss",
                    "objects/" + objectId + "/" + versionId + "/post.png",
                    "post.png",
                    "image/png",
                    4L,
                    "sha256-post",
                    now
            );
            OssUploadSession uploading = OssUploadSession.ready(
                    uuid(7213),
                    objectId,
                    versionId,
                    "PROXY",
                    "community-app",
                    "content",
                    "post-media-draft",
                    "asset-7",
                    "post.png",
                    "image/png",
                    4L,
                    "sha256-post",
                    "actor-7",
                    now.minusSeconds(120),
                    now.plusSeconds(900)
            ).startUploading(now.minusSeconds(120));
            object.set(stagedObject);
            version.set(stagedVersion);
            session.set(uploading);
            objectStore.seedStored(
                    stagedVersion.storageBucket(),
                    ObjectUploadApplicationService.attemptStorageKey(uploading),
                    stagedVersion.contentType(),
                    stagedVersion.contentLength()
            );
        }

        private static boolean matchesClaim(OssUploadSession current, long claimVersion) {
            return current != null
                    && current.status() == OssUploadSessionStatus.UPLOADING
                    && current.claimVersion() == claimVersion;
        }
    }

    private static final class CountingObjectStore implements ObjectStore {
        private final AtomicInteger putCalls = new AtomicInteger();
        private final AtomicInteger headCalls = new AtomicInteger();
        private final AtomicInteger deleteCalls = new AtomicInteger();
        private final AtomicBoolean blockFirstPut = new AtomicBoolean();
        private final AtomicBoolean failNextPut = new AtomicBoolean();
        private final AtomicBoolean failNextHead = new AtomicBoolean();
        private final AtomicReference<MetadataOverride> nextHeadMetadataOverride = new AtomicReference<>();
        private final AtomicBoolean completeFailedPutAfterRetry = new AtomicBoolean();
        private final CountDownLatch firstPutEntered = new CountDownLatch(1);
        private final CountDownLatch duplicatePutEntered = new CountDownLatch(1);
        private final CountDownLatch releaseFirstPut = new CountDownLatch(1);
        private final CountDownLatch releaseDelayedFailedPut = new CountDownLatch(1);
        private final CountDownLatch delayedFailedPutCompleted = new CountDownLatch(1);
        private final List<String> putKeys = new CopyOnWriteArrayList<>();
        private final Map<String, StoredEntry> storedObjects = new ConcurrentHashMap<>();

        @Override
        public void put(String bucket, String key, InputStream content, long contentLength, String contentType) {
            int invocation = putCalls.incrementAndGet();
            putKeys.add(key);
            byte[] payload;
            try {
                payload = content.readAllBytes();
            } catch (Exception failure) {
                throw new IllegalStateException("cannot read test upload content", failure);
            }
            if (failNextPut.compareAndSet(true, false)) {
                if (completeFailedPutAfterRetry.get()) {
                    Thread delayedWrite = new Thread(() -> {
                        await(releaseDelayedFailedPut);
                        seedStored(bucket, key, contentType, contentLength, "etag-put-1", payload);
                        delayedFailedPutCompleted.countDown();
                    }, "delayed-failed-object-put");
                    delayedWrite.setDaemon(true);
                    delayedWrite.start();
                }
                throw new IllegalStateException("simulated object store put failure");
            }
            if (invocation == 1) {
                firstPutEntered.countDown();
                if (blockFirstPut.get()) {
                    await(releaseFirstPut);
                }
            } else {
                duplicatePutEntered.countDown();
            }
            seedStored(bucket, key, contentType, contentLength, "etag-put-" + invocation, payload);
        }

        @Override
        public Optional<ObjectStoreObject> head(String bucket, String key) {
            headCalls.incrementAndGet();
            if (failNextHead.compareAndSet(true, false)) {
                throw new IllegalStateException("simulated object store head failure");
            }
            Optional<ObjectStoreObject> stored = Optional.ofNullable(storedObjects.get(storeId(bucket, key)))
                    .map(StoredEntry::metadata);
            MetadataOverride override = nextHeadMetadataOverride.getAndSet(null);
            if (override == null || stored.isEmpty()) {
                return stored;
            }
            ObjectStoreObject metadata = stored.get();
            return Optional.of(new ObjectStoreObject(
                    metadata.bucket(),
                    metadata.key(),
                    override.contentType(),
                    override.contentLength(),
                    metadata.etag(),
                    metadata.lastModified()
            ));
        }

        @Override
        public StoredObject get(String bucket, String key) {
            StoredEntry stored = storedObjects.get(storeId(bucket, key));
            if (stored == null) {
                throw new IllegalStateException("stored object not found");
            }
            return new StoredObject(
                    new ByteArrayInputStream(stored.payload()),
                    stored.metadata().contentType(),
                    stored.metadata().contentLength()
            );
        }

        @Override
        public void delete(String bucket, String key) {
            deleteCalls.incrementAndGet();
            storedObjects.remove(storeId(bucket, key));
        }

        @Override
        public PresignedObjectUrl presignUpload(String bucket, String key, Duration ttl, String contentType) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public PresignedObjectUrl presignDownload(String bucket, String key, Duration ttl) {
            throw new UnsupportedOperationException("not used");
        }

        private void seedStored(String bucket, String key, String contentType, long contentLength) {
            seedStored(bucket, key, contentType, contentLength, "etag-post");
            putCalls.compareAndSet(0, 1);
        }

        private void overrideNextHeadMetadata(String contentType, long contentLength) {
            nextHeadMetadataOverride.set(new MetadataOverride(contentType, contentLength));
        }

        private void seedStored(
                String bucket,
                String key,
                String contentType,
                long contentLength,
                String etag
        ) {
            seedStored(bucket, key, contentType, contentLength, etag, new byte[Math.toIntExact(contentLength)]);
        }

        private void seedStored(
                String bucket,
                String key,
                String contentType,
                long contentLength,
                String etag,
                byte[] payload
        ) {
            storedObjects.put(storeId(bucket, key), new StoredEntry(
                    new ObjectStoreObject(
                            bucket, key, contentType, contentLength, etag, CLOCK.instant()),
                    payload.clone()
            ));
        }

        private static String storeId(String bucket, String key) {
            return bucket + '\n' + key;
        }

        private record StoredEntry(ObjectStoreObject metadata, byte[] payload) {
        }

        private record MetadataOverride(String contentType, long contentLength) {
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting for concurrent upload test");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", error);
            }
        }
    }
}
