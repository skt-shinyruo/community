package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.command.PreparePostMediaUploadCommand;
import com.nowcoder.community.content.application.result.PostMediaUploadSessionResult;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaAssetLifecycle;
import com.nowcoder.community.content.domain.model.PostMediaKind;
import com.nowcoder.community.content.domain.model.PostMediaReferenceStatus;
import com.nowcoder.community.content.domain.model.PostMediaUploadStatus;
import com.nowcoder.community.content.domain.model.PostVideoState;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostMediaUploadReliabilityContractTest {

    private static final UUID ACTOR_ID = uuid(7001);
    private static final UUID REQUEST_ID = uuid(7002);
    private static final UUID OBJECT_ID = uuid(7003);
    private static final UUID VERSION_ID = uuid(7004);
    private static final UUID SESSION_ID = uuid(7005);
    private static final Date STALE_AT = Date.from(Instant.parse("2026-07-15T00:00:00Z"));

    @Test
    void successfulPrepareReplayMustReuseDeterministicAssetAndPersistOnlyOnce() {
        PreparePostMediaUploadCommand command = prepareCommand(REQUEST_ID);
        AtomicReference<PostMediaAsset> persisted = new AtomicReference<>();
        AtomicInteger createCalls = new AtomicInteger();
        PostMediaAssetRepository repository = mock(PostMediaAssetRepository.class);
        when(repository.createDraft(any())).thenAnswer(invocation -> {
            PostMediaAsset draft = invocation.getArgument(0);
            if (!persisted.compareAndSet(null, draft)) {
                throw new IllegalStateException("duplicate deterministic media draft");
            }
            createCalls.incrementAndGet();
            return draft.id();
        });
        when(repository.getRequired(REQUEST_ID)).thenAnswer(invocation -> persisted.get());
        PostMediaStoragePort storage = mock(PostMediaStoragePort.class);
        when(storage.prepareUpload(any(), anyString())).thenAnswer(invocation ->
                uploadSession(invocation.getArgument(0, PostMediaAsset.class).id()));
        PostMediaApplicationService service = new PostMediaApplicationService(repository, storage);

        PostMediaUploadSessionResult first = service.prepareUpload(command);
        PostMediaUploadSessionResult replay = service.prepareUpload(command);

        assertThat(first.assetId()).isEqualTo(REQUEST_ID);
        assertThat(replay).isEqualTo(first);
        assertThat(createCalls).hasValue(1);
    }

    @Test
    void prepareResponseLossRetryMustReuseRequestIdentityAndMustNotBestEffortDelete() {
        PreparePostMediaUploadCommand command = prepareCommand(REQUEST_ID);
        PostMediaAssetRepository repository = mock(PostMediaAssetRepository.class);
        PostMediaStoragePort storage = mock(PostMediaStoragePort.class);
        List<UUID> attemptedAssetIds = new ArrayList<>();
        when(storage.prepareUpload(any(), anyString())).thenAnswer(invocation -> {
            UUID assetId = invocation.getArgument(0, PostMediaAsset.class).id();
            attemptedAssetIds.add(assetId);
            if (attemptedAssetIds.size() == 1) {
                throw new IllegalStateException("response lost after OSS accepted prepare");
            }
            return uploadSession(assetId);
        });
        PostMediaApplicationService service = new PostMediaApplicationService(repository, storage);

        assertThatThrownBy(() -> service.prepareUpload(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("response lost");
        PostMediaUploadSessionResult replay = service.prepareUpload(command);

        assertThat(attemptedAssetIds).containsExactly(REQUEST_ID, REQUEST_ID);
        assertThat(replay.assetId()).isEqualTo(REQUEST_ID);
        verify(storage, never()).deleteDraftObject(any(), any());
    }

    @Test
    void prepareDbFinalizeFailureMustReplaySameRemoteSessionWithoutCleanup() {
        PreparePostMediaUploadCommand command = prepareCommand(REQUEST_ID);
        PostMediaAssetRepository repository = mock(PostMediaAssetRepository.class);
        AtomicInteger createCalls = new AtomicInteger();
        when(repository.createDraft(any())).thenAnswer(invocation -> {
            if (createCalls.getAndIncrement() == 0) {
                throw new IllegalStateException("content DB finalize failed");
            }
            return invocation.getArgument(0, PostMediaAsset.class).id();
        });
        PostMediaStoragePort storage = mock(PostMediaStoragePort.class);
        List<UUID> attemptedAssetIds = new ArrayList<>();
        when(storage.prepareUpload(any(), anyString())).thenAnswer(invocation -> {
            UUID assetId = invocation.getArgument(0, PostMediaAsset.class).id();
            attemptedAssetIds.add(assetId);
            return uploadSession(assetId);
        });
        PostMediaApplicationService service = new PostMediaApplicationService(repository, storage);

        Throwable firstFailure = catchThrowable(() -> service.prepareUpload(command));
        PostMediaUploadSessionResult replay = service.prepareUpload(command);

        assertThat(firstFailure).isInstanceOf(IllegalStateException.class)
                .hasMessage("content DB finalize failed");
        assertThat(attemptedAssetIds).containsExactly(REQUEST_ID, REQUEST_ID);
        assertThat(replay).isEqualTo(uploadSession(REQUEST_ID));
        verify(storage, never()).deleteDraftObject(any(), any());
    }

    @Test
    void uploadStateMachineMustExposeOnlyTheRequiredRecoveryTransitions() throws Exception {
        Class<? extends Enum<?>> statusType = uploadStatusType();
        assertThat(enumNames(statusType)).containsExactlyInAnyOrder(
                "PREPARED", "COMPLETING", "OBJECT_COMPLETED", "COMPLETED", "FAILED");
        Method canTransitionTo = statusType.getMethod("canTransitionTo", statusType);

        assertTransition(canTransitionTo, statusType, "PREPARED", "COMPLETING", true);
        assertTransition(canTransitionTo, statusType, "COMPLETING", "PREPARED", true);
        assertTransition(canTransitionTo, statusType, "COMPLETING", "OBJECT_COMPLETED", true);
        assertTransition(canTransitionTo, statusType, "COMPLETING", "FAILED", true);
        assertTransition(canTransitionTo, statusType, "OBJECT_COMPLETED", "COMPLETED", true);
        assertTransition(canTransitionTo, statusType, "OBJECT_COMPLETED", "FAILED", true);
        assertTransition(canTransitionTo, statusType, "PREPARED", "COMPLETED", false);
        assertTransition(canTransitionTo, statusType, "COMPLETED", "COMPLETING", false);
        assertTransition(canTransitionTo, statusType, "FAILED", "COMPLETING", false);
    }

    @Test
    void aggregateAndRepositoryMustCarryVersionedCasAndStaleRecoveryState() throws Exception {
        Class<? extends Enum<?>> statusType = uploadStatusType();
        Map<String, Class<?>> aggregateComponents = Arrays.stream(PostMediaAsset.class.getRecordComponents())
                .collect(java.util.stream.Collectors.toMap(RecordComponent::getName, RecordComponent::getType));
        assertThat(aggregateComponents)
                .containsEntry("uploadStatus", statusType)
                .containsEntry("uploadOperationVersion", long.class)
                .containsEntry("uploadUpdatedAt", Date.class);

        assertBooleanMethod("claimUploadCompletion", UUID.class, UUID.class, long.class, Date.class);
        assertBooleanMethod(
                "markObjectCompleted", UUID.class, long.class, UUID.class, String.class,
                String.class, long.class, Date.class);
        assertBooleanMethod("markUploadCompleted", UUID.class, long.class, Date.class);
        assertBooleanMethod("markUploadFailed", UUID.class, long.class, String.class, Date.class);
        assertBooleanMethod(
                "resetStaleUploadCompletion", UUID.class, long.class, Date.class, Date.class);
        assertBooleanMethod(
                "recordUploadRecoveryFailure",
                UUID.class, long.class, Date.class, String.class, Date.class);
        Method staleScan = PostMediaAssetRepository.class.getMethod("listStaleCompleting", Date.class, int.class);
        assertThat(staleScan.getReturnType()).isEqualTo(List.class);
    }

    @Test
    void unknownCanonicalMetadataMustResetTheStaleClaimForRetryWithoutCleanup() throws Exception {
        RecoveryHarness harness = recoveryHarness("UNKNOWN");

        harness.executeRecovery();

        assertThat(harness.repositoryCalls()).contains("resetStaleUploadCompletion");
        assertThat(harness.repositoryCalls()).noneMatch(PostMediaUploadReliabilityContractTest::isTerminalWrite);
        assertThat(harness.storageCalls()).doesNotContain("deleteDraftObject");
    }

    @Test
    void transientCompleteFailureMustResetAfterStaleAndConvergeOnRetry() {
        AtomicReference<PostMediaAsset> current = new AtomicReference<>(preparedAsset(REQUEST_ID, 0L));
        PostMediaAssetRepository repository = statefulUploadRepository(current);
        PostMediaStoragePort storage = mock(PostMediaStoragePort.class);
        AtomicInteger completeCalls = new AtomicInteger();
        when(storage.completeUpload(any(), eq(SESSION_ID), any())).thenAnswer(invocation -> {
            if (completeCalls.getAndIncrement() == 0) {
                throw new IllegalStateException("transient OSS write failure");
            }
            return new PostMediaStoragePort.UploadedPostMedia(
                    VERSION_ID, "https://cdn.example.test/post.png", "image/png", 4L);
        });
        when(storage.queryCanonicalMetadata(any())).thenReturn(new PostMediaStoragePort.CanonicalPostMedia(
                PostMediaStoragePort.CanonicalMetadataOutcome.UNKNOWN,
                OBJECT_ID,
                null,
                "",
                "",
                0L,
                ""
        ));
        PostMediaApplicationService uploadService = new PostMediaApplicationService(repository, storage);
        PostMediaUploadRecoveryApplicationService recoveryService =
                new PostMediaUploadRecoveryApplicationService(repository, storage);
        PostMediaUploadContent content = new PostMediaUploadContent(
                () -> new ByteArrayInputStream(new byte[]{1, 2, 3, 4}),
                "image/png",
                4L,
                "sha256-post"
        );

        assertThatThrownBy(() -> uploadService.completeUpload(ACTOR_ID, REQUEST_ID, SESSION_ID, content))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("transient OSS write failure");
        assertThat(current.get().uploadStatus()).isEqualTo(PostMediaUploadStatus.COMPLETING);

        Date staleBefore = new Date(System.currentTimeMillis() + 1_000L);
        recoveryService.recoverStaleCompleting(staleBefore, 10);
        assertThat(current.get().uploadStatus()).isEqualTo(PostMediaUploadStatus.PREPARED);
        assertThat(current.get().uploadOperationVersion()).isEqualTo(2L);

        uploadService.completeUpload(ACTOR_ID, REQUEST_ID, SESSION_ID, content);

        assertThat(current.get().uploadStatus()).isEqualTo(PostMediaUploadStatus.COMPLETED);
        assertThat(current.get().lifecycle()).isEqualTo(PostMediaAssetLifecycle.UPLOADED);
        assertThat(current.get().uploadOperationVersion()).isEqualTo(3L);
        assertThat(completeCalls).hasValue(2);
        verify(storage, never()).deleteDraftObject(any(), any());
    }

    @Test
    void recoveryFailureForOneAssetMustNotBlockTheRemainingBatch() throws Exception {
        PostMediaAsset failed = staleCompletingAsset(uuid(7091));
        PostMediaAsset recoverable = staleCompletingAsset(uuid(7092));
        PostMediaAssetRepository repository = mock(PostMediaAssetRepository.class);
        when(repository.listStaleCompleting(STALE_AT, 10)).thenReturn(List.of(failed, recoverable));
        when(repository.resetStaleUploadCompletion(any(), any(Long.class), eq(STALE_AT), any(Date.class)))
                .thenReturn(true);
        PostMediaStoragePort storage = mock(PostMediaStoragePort.class);
        when(storage.queryCanonicalMetadata(failed))
                .thenThrow(new IllegalStateException("bad first asset"));
        when(storage.queryCanonicalMetadata(recoverable)).thenReturn(new PostMediaStoragePort.CanonicalPostMedia(
                PostMediaStoragePort.CanonicalMetadataOutcome.UNKNOWN,
                recoverable.ossObjectId(),
                null,
                "",
                "",
                0L,
                ""
        ));
        PostMediaUploadRecoveryApplicationService service =
                new PostMediaUploadRecoveryApplicationService(repository, storage);

        service.recoverStaleCompleting(STALE_AT, 10);

        verify(repository).resetStaleUploadCompletion(
                eq(recoverable.id()), eq(recoverable.uploadOperationVersion()), eq(STALE_AT), any(Date.class));
    }

    @Test
    void recoveryFailureMustRotateAPoisonCandidateSoRowsBeyondTheBatchLimitCanRun() throws Exception {
        PostMediaAsset poison = staleCompletingAsset(uuid(7093));
        PostMediaAsset firstHealthy = staleCompletingAsset(uuid(7094));
        PostMediaAsset beyondFirstBatch = staleCompletingAsset(uuid(7095));
        List<PostMediaAsset> all = List.of(poison, firstHealthy, beyondFirstBatch);
        Map<UUID, Date> eligibleAt = new HashMap<>();
        all.forEach(asset -> eligibleAt.put(asset.id(), STALE_AT));
        Set<UUID> recovered = new HashSet<>();
        Date retryAt = Date.from(Instant.parse("2026-07-15T00:10:00Z"));

        PostMediaAssetRepository repository = mock(PostMediaAssetRepository.class);
        when(repository.listStaleCompleting(STALE_AT, 2)).thenAnswer(invocation -> all.stream()
                .filter(asset -> !recovered.contains(asset.id()))
                .filter(asset -> !eligibleAt.get(asset.id()).after(STALE_AT))
                .limit(2)
                .toList());
        when(repository.recordUploadRecoveryFailure(
                any(), any(Long.class), eq(STALE_AT), anyString(), eq(retryAt)))
                .thenAnswer(invocation -> {
                    UUID assetId = invocation.getArgument(0);
                    eligibleAt.put(assetId, retryAt);
                    return true;
                });
        when(repository.resetStaleUploadCompletion(
                any(), any(Long.class), eq(STALE_AT), eq(retryAt)))
                .thenAnswer(invocation -> recovered.add(invocation.getArgument(0)));

        PostMediaStoragePort storage = mock(PostMediaStoragePort.class);
        when(storage.queryCanonicalMetadata(poison))
                .thenThrow(new IllegalStateException("poison canonical lookup"));
        when(storage.queryCanonicalMetadata(firstHealthy)).thenReturn(unknownCanonical(firstHealthy));
        when(storage.queryCanonicalMetadata(beyondFirstBatch)).thenReturn(unknownCanonical(beyondFirstBatch));
        PostMediaUploadRecoveryApplicationService service = new PostMediaUploadRecoveryApplicationService(
                repository,
                storage,
                new PostMediaUploadTransactionOperations(repository),
                Clock.fixed(retryAt.toInstant(), ZoneOffset.UTC)
        );

        service.recoverStaleCompleting(STALE_AT, 2);
        service.recoverStaleCompleting(STALE_AT, 2);

        verify(repository).recordUploadRecoveryFailure(
                eq(poison.id()), eq(poison.uploadOperationVersion()), eq(STALE_AT),
                org.mockito.ArgumentMatchers.contains("poison canonical lookup"), eq(retryAt));
        verify(storage).queryCanonicalMetadata(beyondFirstBatch);
        assertThat(recovered).containsExactlyInAnyOrder(firstHealthy.id(), beyondFirstBatch.id());
    }

    @Test
    void foundCanonicalMetadataMustPersistObjectMetadataBeforeFinalCompletion() throws Exception {
        RecoveryHarness harness = recoveryHarness("FOUND");

        harness.executeRecovery();

        assertThat(harness.repositoryCalls()).containsSubsequence("markObjectCompleted", "markUploadCompleted");
        assertThat(harness.repositoryCalls()).doesNotContain("markUploadFailed", "markDraftDeleted");
        assertThat(harness.storageCalls()).doesNotContain("deleteDraftObject");
    }

    @Test
    void prepareAndCompleteOrchestratorMustNotContainBestEffortCleanupPaths() {
        assertThat(Arrays.stream(PostMediaApplicationService.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("cleanupPreparedDraft", "cleanupUploadedDraft");
    }

    private static PreparePostMediaUploadCommand prepareCommand(UUID requestId) {
        RecordComponent[] components = PreparePostMediaUploadCommand.class.getRecordComponents();
        assertThat(Arrays.stream(components).map(RecordComponent::getName))
                .as("prepare command needs a caller-supplied deterministic requestId")
                .contains("requestId");
        Object[] arguments = Arrays.stream(components)
                .map(component -> prepareValue(component.getName(), requestId))
                .toArray();
        try {
            Constructor<PreparePostMediaUploadCommand> constructor = PreparePostMediaUploadCommand.class
                    .getDeclaredConstructor(Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new));
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("cannot create deterministic prepare command", error);
        }
    }

    private static Object prepareValue(String name, UUID requestId) {
        return switch (name) {
            case "actorUserId" -> ACTOR_ID;
            case "requestId" -> requestId;
            case "fileName" -> "post.png";
            case "contentType" -> "image/png";
            case "contentLength" -> 4L;
            case "mediaKind" -> "IMAGE";
            case "checksumSha256" -> "sha256-post";
            default -> throw new AssertionError("unexpected prepare command component: " + name);
        };
    }

    private static PostMediaUploadSessionResult uploadSession(UUID assetId) {
        return new PostMediaUploadSessionResult(
                assetId,
                SESSION_ID.toString(),
                "/api/posts/media/" + assetId + "/upload",
                "POST",
                "file",
                "uploadId",
                100L,
                "image/png",
                Instant.parse("2026-07-15T00:15:00Z"),
                OBJECT_ID,
                VERSION_ID
        );
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Enum<?>> uploadStatusType() throws ClassNotFoundException {
        Class<?> type = Class.forName("com.nowcoder.community.content.domain.model.PostMediaUploadStatus");
        assertThat(type.isEnum()).isTrue();
        return (Class<? extends Enum<?>>) type;
    }

    private static List<String> enumNames(Class<? extends Enum<?>> enumType) {
        return Arrays.stream(enumType.getEnumConstants()).map(Enum::name).toList();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertTransition(
            Method method,
            Class<? extends Enum<?>> statusType,
            String from,
            String to,
            boolean expected
    ) throws ReflectiveOperationException {
        Enum source = Enum.valueOf((Class) statusType, from);
        Enum target = Enum.valueOf((Class) statusType, to);
        assertThat(method.invoke(source, target)).isEqualTo(expected);
    }

    private static void assertBooleanMethod(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        assertThat(PostMediaAssetRepository.class.getMethod(name, parameterTypes).getReturnType())
                .isEqualTo(boolean.class);
    }

    private static RecoveryHarness recoveryHarness(String outcome) throws Exception {
        Class<?> recoveryType = Class.forName(
                "com.nowcoder.community.content.application.PostMediaUploadRecoveryApplicationService");
        Method queryCanonicalMetadata = PostMediaStoragePort.class.getMethod(
                "queryCanonicalMetadata", PostMediaAsset.class);
        PostMediaAsset staleAsset = staleCompletingAsset();
        List<String> repositoryCalls = new ArrayList<>();
        PostMediaAssetRepository repository = mock(PostMediaAssetRepository.class, invocation -> {
            String name = invocation.getMethod().getName();
            repositoryCalls.add(name);
            if (name.equals("listStaleCompleting")) {
                return List.of(staleAsset);
            }
            if (invocation.getMethod().getReturnType() == boolean.class) {
                return true;
            }
            if (invocation.getMethod().getReturnType() == PostMediaAsset.class) {
                return staleAsset;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        List<String> storageCalls = new ArrayList<>();
        PostMediaStoragePort storage = mock(PostMediaStoragePort.class, invocation -> {
            String name = invocation.getMethod().getName();
            storageCalls.add(name);
            if (name.equals(queryCanonicalMetadata.getName())) {
                return canonicalResult(queryCanonicalMetadata.getReturnType(), outcome);
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        Object service = instantiateRecovery(recoveryType, repository, storage);
        Method recover = recoveryType.getMethod("recoverStaleCompleting", Date.class, int.class);
        return new RecoveryHarness(service, recover, repositoryCalls, storageCalls);
    }

    private static Object instantiateRecovery(
            Class<?> recoveryType,
            PostMediaAssetRepository repository,
            PostMediaStoragePort storage
    ) throws Exception {
        Constructor<?> constructor = Arrays.stream(recoveryType.getDeclaredConstructors())
                .min(java.util.Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow();
        Object[] arguments = Arrays.stream(constructor.getParameterTypes()).map(type -> {
            if (type == PostMediaAssetRepository.class) {
                return repository;
            }
            if (type == PostMediaStoragePort.class) {
                return storage;
            }
            if (type == Clock.class) {
                return Clock.fixed(Instant.parse("2026-07-15T01:00:00Z"), ZoneOffset.UTC);
            }
            if (type.isInterface()) {
                return mock(type);
            }
            throw new AssertionError("unsupported recovery dependency: " + type.getName());
        }).toArray();
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    private static Object canonicalResult(Class<?> resultType, String outcome) throws Exception {
        assertThat(resultType.isRecord()).isTrue();
        RecordComponent[] components = resultType.getRecordComponents();
        Object[] arguments = Arrays.stream(components)
                .map(component -> canonicalValue(component, outcome))
                .toArray();
        Constructor<?> constructor = resultType.getDeclaredConstructor(
                Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new));
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object canonicalValue(RecordComponent component, String outcome) {
        return switch (component.getName()) {
            case "outcome" -> {
                assertThat(component.getType().isEnum()).isTrue();
                assertThat(Arrays.stream(component.getType().getEnumConstants()).map(value -> ((Enum<?>) value).name()))
                        .contains("FOUND", "NOT_FOUND", "UNKNOWN");
                yield Enum.valueOf((Class) component.getType(), outcome);
            }
            case "objectId" -> OBJECT_ID;
            case "versionId", "currentVersionId" -> VERSION_ID;
            case "publicUrl" -> "https://cdn.example.test/post.png";
            case "contentType" -> "image/png";
            case "contentLength" -> 4L;
            case "checksumSha256" -> "sha256-post";
            default -> defaultValue(component.getType());
        };
    }

    private static PostMediaAsset staleCompletingAsset() throws Exception {
        return staleCompletingAsset(REQUEST_ID);
    }

    private static PostMediaAsset staleCompletingAsset(UUID assetId) throws Exception {
        Class<? extends Enum<?>> uploadStatus = uploadStatusType();
        RecordComponent[] components = PostMediaAsset.class.getRecordComponents();
        assertThat(Arrays.stream(components).map(RecordComponent::getName)).contains("uploadStatus");
        Object[] arguments = Arrays.stream(components)
                .map(component -> assetValue(component, uploadStatus, assetId))
                .toArray();
        Constructor<PostMediaAsset> constructor = PostMediaAsset.class.getDeclaredConstructor(
                Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new));
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object assetValue(
            RecordComponent component,
            Class<? extends Enum<?>> uploadStatus,
            UUID assetId
    ) {
        return switch (component.getName()) {
            case "id" -> assetId;
            case "ownerUserId" -> ACTOR_ID;
            case "ossObjectId" -> OBJECT_ID;
            case "ossVersionId" -> VERSION_ID;
            case "uploadSessionId" -> SESSION_ID;
            case "fileName" -> "post.png";
            case "contentType" -> "image/png";
            case "contentLength" -> 4L;
            case "mediaKind" -> PostMediaKind.IMAGE;
            case "lifecycle" -> PostMediaAssetLifecycle.DRAFT;
            case "uploadStatus" -> Enum.valueOf((Class) uploadStatus, "COMPLETING");
            case "uploadOperationVersion" -> 7L;
            case "uploadUpdatedAt", "createTime", "updateTime" -> STALE_AT;
            case "videoState" -> PostVideoState.NONE;
            case "publicUrl", "failureReason" -> "";
            default -> defaultValue(component.getType());
        };
    }

    private static PostMediaAsset preparedAsset(UUID assetId, long operationVersion) {
        return new PostMediaAsset(
                assetId,
                ACTOR_ID,
                null,
                OBJECT_ID,
                VERSION_ID,
                null,
                SESSION_ID,
                "post.png",
                "image/png",
                4L,
                PostMediaKind.IMAGE,
                PostMediaAssetLifecycle.DRAFT,
                PostMediaUploadStatus.PREPARED,
                operationVersion,
                STALE_AT,
                PostMediaReferenceStatus.UNBOUND,
                0L,
                STALE_AT,
                PostVideoState.NONE,
                "",
                "",
                STALE_AT,
                STALE_AT
        );
    }

    private static PostMediaAssetRepository statefulUploadRepository(AtomicReference<PostMediaAsset> current) {
        PostMediaAssetRepository repository = mock(PostMediaAssetRepository.class);
        when(repository.getRequired(REQUEST_ID)).thenAnswer(invocation -> current.get());
        when(repository.listStaleCompleting(any(Date.class), eq(10)))
                .thenAnswer(invocation -> List.of(current.get()));
        when(repository.claimUploadCompletion(eq(REQUEST_ID), eq(ACTOR_ID), any(Long.class), any(Date.class)))
                .thenAnswer(invocation -> {
                    long expectedVersion = invocation.getArgument(2);
                    Date updateTime = invocation.getArgument(3);
                    PostMediaAsset value = current.get();
                    if (value.uploadStatus() != PostMediaUploadStatus.PREPARED
                            || value.uploadOperationVersion() != expectedVersion) {
                        return false;
                    }
                    current.set(withUploadState(
                            value,
                            PostMediaAssetLifecycle.DRAFT,
                            PostMediaUploadStatus.COMPLETING,
                            expectedVersion + 1L,
                            updateTime,
                            value.ossVersionId(),
                            value.publicUrl()
                    ));
                    return true;
                });
        when(repository.resetStaleUploadCompletion(
                eq(REQUEST_ID), any(Long.class), any(Date.class), any(Date.class)))
                .thenAnswer(invocation -> {
                    long expectedVersion = invocation.getArgument(1);
                    Date staleBefore = invocation.getArgument(2);
                    Date updateTime = invocation.getArgument(3);
                    PostMediaAsset value = current.get();
                    if (value.uploadStatus() != PostMediaUploadStatus.COMPLETING
                            || value.uploadOperationVersion() != expectedVersion
                            || value.uploadUpdatedAt().after(staleBefore)) {
                        return false;
                    }
                    current.set(withUploadState(
                            value,
                            PostMediaAssetLifecycle.DRAFT,
                            PostMediaUploadStatus.PREPARED,
                            expectedVersion + 1L,
                            updateTime,
                            value.ossVersionId(),
                            value.publicUrl()
                    ));
                    return true;
                });
        when(repository.markObjectCompleted(
                eq(REQUEST_ID), any(Long.class), any(), anyString(), anyString(), any(Long.class), any(Date.class)))
                .thenAnswer(invocation -> {
                    long expectedVersion = invocation.getArgument(1);
                    PostMediaAsset value = current.get();
                    if (value.uploadStatus() != PostMediaUploadStatus.COMPLETING
                            || value.uploadOperationVersion() != expectedVersion) {
                        return false;
                    }
                    current.set(withUploadState(
                            value,
                            PostMediaAssetLifecycle.DRAFT,
                            PostMediaUploadStatus.OBJECT_COMPLETED,
                            expectedVersion,
                            invocation.getArgument(6),
                            invocation.getArgument(2),
                            invocation.getArgument(3)
                    ));
                    return true;
                });
        when(repository.markUploadCompleted(eq(REQUEST_ID), any(Long.class), any(Date.class)))
                .thenAnswer(invocation -> {
                    long expectedVersion = invocation.getArgument(1);
                    PostMediaAsset value = current.get();
                    if (value.uploadStatus() != PostMediaUploadStatus.OBJECT_COMPLETED
                            || value.uploadOperationVersion() != expectedVersion) {
                        return false;
                    }
                    current.set(withUploadState(
                            value,
                            PostMediaAssetLifecycle.UPLOADED,
                            PostMediaUploadStatus.COMPLETED,
                            expectedVersion,
                            invocation.getArgument(2),
                            value.ossVersionId(),
                            value.publicUrl()
                    ));
                    return true;
                });
        return repository;
    }

    private static PostMediaAsset withUploadState(
            PostMediaAsset value,
            PostMediaAssetLifecycle lifecycle,
            PostMediaUploadStatus uploadStatus,
            long operationVersion,
            Date updateTime,
            UUID versionId,
            String publicUrl
    ) {
        return new PostMediaAsset(
                value.id(),
                value.ownerUserId(),
                value.postId(),
                value.ossObjectId(),
                versionId,
                value.ossReferenceId(),
                value.uploadSessionId(),
                value.fileName(),
                value.contentType(),
                value.contentLength(),
                value.mediaKind(),
                lifecycle,
                uploadStatus,
                operationVersion,
                updateTime,
                value.referenceStatus(),
                value.referenceOperationVersion(),
                value.referenceUpdatedAt(),
                value.videoState(),
                publicUrl,
                value.failureReason(),
                value.createTime(),
                updateTime
        );
    }

    private static PostMediaStoragePort.CanonicalPostMedia unknownCanonical(PostMediaAsset asset) {
        return new PostMediaStoragePort.CanonicalPostMedia(
                PostMediaStoragePort.CanonicalMetadataOutcome.UNKNOWN,
                asset.ossObjectId(),
                null,
                "",
                "",
                0L,
                ""
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == int.class) {
            return 0;
        }
        throw new AssertionError("unsupported primitive: " + type.getName());
    }

    private static boolean isTerminalWrite(String methodName) {
        return methodName.equals("markObjectCompleted")
                || methodName.equals("markUploadCompleted")
                || methodName.equals("markUploadFailed")
                || methodName.equals("markDraftDeleted");
    }

    private record RecoveryHarness(
            Object service,
            Method recover,
            List<String> repositoryCalls,
            List<String> storageCalls
    ) {
        private void executeRecovery() throws Exception {
            recover.invoke(service, STALE_AT, 10);
        }
    }
}
