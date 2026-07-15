package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.CommentDeletion;
import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.CommentThreadDeletion;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.CommentDataObject;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentPersistenceBoundaryContractTest {

    private static final UUID ROOT_ID = uuid(8301);
    private static final UUID AUTHOR_ID = uuid(8302);
    private static final UUID POST_ID = uuid(8303);

    @Test
    void commentDataObjectShouldBeIndependentAndCarryVersion() {
        Class<?> dataObjectType = requireClass(
                "com.nowcoder.community.content.infrastructure.persistence.dataobject.CommentDataObject"
        );

        assertThat(Comment.class.isAssignableFrom(dataObjectType)).isFalse();
        assertThat(Arrays.stream(dataObjectType.getDeclaredFields()).map(field -> field.getName()))
                .contains("version");
    }

    @Test
    void mapperShouldNeverReceiveOrReturnTheAggregate() {
        assertThat(Arrays.stream(CommentMapper.class.getDeclaredMethods())
                .filter(this::mentionsAggregate)
                .map(Method::toGenericString))
                .as("CommentMapper must not receive or return the Comment aggregate")
                .isEmpty();
    }

    @Test
    void mapperAndRepositoryShouldRetireSqlShapedCommentWriteMethods() {
        assertThat(Arrays.stream(CommentMapper.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain(
                        "updateCommentContent",
                        "updateModerationDeleteMeta",
                        "updateActiveCommentDeleted",
                        "selectActiveRepliesByRootComment"
                );
        assertThat(Arrays.stream(CommentRepository.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("updateContent", "markActiveThreadDeleted");
    }

    @Test
    void repositoryShouldApplyVersionedTransitionsAndExposeStableOutcomes() {
        Class<?> editType = requireClass("com.nowcoder.community.content.domain.model.CommentEdit");
        Class<?> deletionType = requireClass("com.nowcoder.community.content.domain.model.CommentDeletion");
        Class<?> threadType = requireClass("com.nowcoder.community.content.domain.model.CommentThreadDeletion");

        Method editApply = requiredApply(editType);
        Method deletionApply = requiredApply(deletionType);
        Method threadApply = requiredApply(threadType);
        assertOutcomeEnum(editApply.getReturnType());
        assertThat(deletionApply.getReturnType()).isEqualTo(CommentDeletionResult.class);
        assertThat(threadApply.getReturnType()).isEqualTo(CommentDeletionResult.class);

        Method statusAccessor = Arrays.stream(CommentDeletionResult.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("status") && method.getParameterCount() == 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("CommentDeletionResult must expose status()"));
        assertOutcomeEnum(statusAccessor.getReturnType());
    }

    @Test
    void deletionResultShouldBindCardinalityToAppliedNoOpStaleAndNotFoundOutcomes() {
        Method statusAccessor = Arrays.stream(CommentDeletionResult.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("status") && method.getParameterCount() == 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("CommentDeletionResult must expose status()"));
        Class<?> statusType = statusAccessor.getReturnType();
        assertOutcomeEnum(statusType);
        Object applied = enumValue(statusType, "APPLIED");
        Object noOp = enumValue(statusType, "NO_OP");
        Object stale = enumValue(statusType, "STALE");
        Object notFound = enumValue(statusType, "NOT_FOUND");
        Object first = legacySnapshot(uuid(8397));
        Object second = legacySnapshot(uuid(8398));

        Object appliedResult = deletionResult(applied, List.of(first, second));
        Object noOpResult = deletionResult(noOp, List.of());
        Object staleResult = deletionResult(stale, List.of());
        Object notFoundResult = deletionResult(notFound, List.of());

        assertThat(invoke(statusAccessor, appliedResult)).hasToString("APPLIED");
        assertThat(invoke(statusAccessor, noOpResult)).hasToString("NO_OP");
        assertThat(invoke(statusAccessor, staleResult)).hasToString("STALE");
        assertThat(invoke(statusAccessor, notFoundResult)).hasToString("NOT_FOUND");
        assertThat(((CommentDeletionResult) appliedResult).deletedCount()).isEqualTo(2);
        assertThat(((CommentDeletionResult) noOpResult).deletedCount()).isZero();
        assertThat(((CommentDeletionResult) staleResult).deletedCount()).isZero();
        assertThat(((CommentDeletionResult) notFoundResult).deletedCount()).isZero();
        assertThatThrownByConstruction(statusType, noOp);
        assertThatThrownByConstruction(statusType, stale);
        assertThatThrownByConstruction(statusType, notFound);
    }

    @Test
    void rootDeletionMustNotLeaveAReplyInsertedAfterItsThreadSnapshot() throws Exception {
        CommentMapper mapper = mock(CommentMapper.class);
        UuidV7Generator idGenerator = new UuidV7Generator(Clock.fixed(
                Instant.parse("2026-07-15T09:00:00Z"),
                ZoneOffset.UTC
        ));
        MyBatisCommentRepository repository = new MyBatisCommentRepository(mapper, idGenerator);
        CommentDataObject root = row(ROOT_ID, null, 0, 7L);
        CountDownLatch threadSnapshotCaptured = new CountDownLatch(1);
        CountDownLatch replyRootLockAttempted = new CountDownLatch(1);
        CountDownLatch rootDeleted = new CountDownLatch(1);
        AtomicInteger threadReads = new AtomicInteger();
        AtomicReference<CommentDataObject> rootState = new AtomicReference<>(root);

        when(mapper.selectThreadForUpdate(ROOT_ID)).thenAnswer(invocation -> {
            if (threadReads.incrementAndGet() == 1) {
                threadSnapshotCaptured.countDown();
                assertThat(replyRootLockAttempted.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return List.of(rootState.get());
        });
        when(mapper.selectByIdForUpdate(ROOT_ID)).thenAnswer(invocation -> {
            replyRootLockAttempted.countDown();
            assertThat(rootDeleted.await(5, TimeUnit.SECONDS)).isTrue();
            return rootState.get();
        });
        when(mapper.applyThreadDeletion(
                eq(ROOT_ID), anyList(), eq(AUTHOR_ID), eq("author_delete"), any(Date.class)
        )).thenAnswer(invocation -> {
            rootState.get().setStatus(1);
            rootState.get().setVersion(8L);
            rootDeleted.countDown();
            return 1;
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CommentDeletionResult> deletion = executor.submit(() -> {
                List<CommentSnapshot> activeThread = repository.getActiveThreadSnapshots(ROOT_ID);
                CommentDeletion rootDeletion = new CommentDeletion(
                        ROOT_ID, 7L, AUTHOR_ID, "author_delete", new Date(3_000_000L)
                );
                return repository.apply(CommentThreadDeletion.from(rootDeletion, activeThread));
            });
            assertThat(threadSnapshotCaptured.await(5, TimeUnit.SECONDS)).isTrue();
            Future<UUID> reply = executor.submit(() -> repository.create(new CommentDraft(
                    AUTHOR_ID,
                    POST_ID,
                    ROOT_ID,
                    ROOT_ID,
                    AUTHOR_ID,
                    "late reply",
                    new Date(2_500_000L)
            )));

            CommentDeletionResult result = deletion.get(5, TimeUnit.SECONDS);

            assertThatThrownBy(() -> reply.get(5, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(com.nowcoder.community.common.exception.BusinessException.class);
            assertThat(result.deletedCommentIds())
                    .as("root deletion and reply creation must serialize on the same root lock")
                    .containsExactly(ROOT_ID);
            verify(mapper, never()).insert(any(CommentDataObject.class));
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertThatThrownByConstruction(Class<?> statusType, Object status) {
        Object affected = legacySnapshot(uuid(8399));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> deletionResult(status, List.of(affected)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("affected");
    }

    private Object deletionResult(Object status, List<?> affected) {
        RecordComponent[] components = CommentDeletionResult.class.getRecordComponents();
        assertThat(Arrays.stream(components).map(RecordComponent::getName))
                .containsExactly("status", "deletedComments");
        Class<?>[] parameterTypes = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] arguments = Arrays.stream(components)
                .map(component -> component.getName().equals("status") ? status : affected)
                .toArray();
        try {
            Constructor<CommentDeletionResult> constructor = CommentDeletionResult.class.getDeclaredConstructor(parameterTypes);
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError("CommentDeletionResult construction failed", cause);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("CommentDeletionResult construction failed", error);
        }
    }

    private Object legacySnapshot(UUID id) {
        Class<?> snapshotType = requireClass("com.nowcoder.community.content.domain.model.CommentSnapshot");
        RecordComponent[] components = snapshotType.getRecordComponents();
        try {
            Class<?>[] parameterTypes = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
            Constructor<?> constructor = snapshotType.getDeclaredConstructor(parameterTypes);
            Object[] values = Arrays.stream(components)
                    .map(component -> snapshotValue(component, id))
                    .toArray();
            return constructor.newInstance(values);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("cannot build CommentSnapshot fixture", error);
        }
    }

    private Object snapshotValue(RecordComponent component, UUID id) {
        return switch (component.getName()) {
            case "id", "rootCommentId" -> id;
            case "userId" -> AUTHOR_ID;
            case "postId" -> POST_ID;
            case "parentCommentId", "replyToUserId", "updateTime",
                    "deletedBy", "deletedReason", "deletedTime" -> null;
            case "content" -> "content";
            case "status", "editCount" -> 0;
            case "createTime" -> new Date(1_000_000L);
            case "version" -> 7L;
            default -> throw new AssertionError("Unhandled CommentSnapshot component: " + component.getName());
        };
    }

    private Method requiredApply(Class<?> transitionType) {
        try {
            return CommentRepository.class.getDeclaredMethod("apply", transitionType);
        } catch (NoSuchMethodException error) {
            throw new AssertionError("CommentRepository must apply " + transitionType.getSimpleName(), error);
        }
    }

    private void assertOutcomeEnum(Class<?> type) {
        assertThat(type.isEnum()).as("apply outcome must be an enum").isTrue();
        assertThat(Arrays.stream(type.getEnumConstants()).map(Object::toString))
                .containsExactlyInAnyOrder("APPLIED", "NO_OP", "STALE", "NOT_FOUND");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
    }

    private boolean mentionsAggregate(Method method) {
        if (mentionsAggregate(method.getGenericReturnType())) {
            return true;
        }
        return Arrays.stream(method.getGenericParameterTypes()).anyMatch(this::mentionsAggregate);
    }

    private boolean mentionsAggregate(Type type) {
        if (type instanceof Class<?> rawClass) {
            return rawClass == Comment.class || rawClass.isArray() && mentionsAggregate(rawClass.getComponentType());
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return mentionsAggregate(parameterizedType.getRawType())
                    || Arrays.stream(parameterizedType.getActualTypeArguments()).anyMatch(this::mentionsAggregate);
        }
        if (type instanceof GenericArrayType arrayType) {
            return mentionsAggregate(arrayType.getGenericComponentType());
        }
        if (type instanceof WildcardType wildcardType) {
            return Arrays.stream(wildcardType.getUpperBounds()).anyMatch(this::mentionsAggregate)
                    || Arrays.stream(wildcardType.getLowerBounds()).anyMatch(this::mentionsAggregate);
        }
        if (type instanceof TypeVariable<?> variable) {
            return Arrays.stream(variable.getBounds()).anyMatch(this::mentionsAggregate);
        }
        return false;
    }

    private Object invoke(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("outcome accessor invocation failed", error);
        }
    }

    private Class<?> requireClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException error) {
            throw new AssertionError("Missing required Comment persistence contract: " + className, error);
        }
    }

    private CommentDataObject row(UUID id, UUID parentId, int status, long version) {
        CommentDataObject row = new CommentDataObject();
        row.setId(id);
        row.setPostId(POST_ID);
        row.setUserId(AUTHOR_ID);
        row.setRootCommentId(parentId == null ? id : ROOT_ID);
        row.setParentCommentId(parentId);
        row.setContent("content");
        row.setStatus(status);
        row.setCreateTime(new Date(1_000_000L));
        row.setVersion(version);
        return row;
    }
}
