package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.CommentDeletionResult;
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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    void replyContextMustRejectRootDeletedBetweenHintReadAndLockedReread() {
        CommentMapper mapper = mock(CommentMapper.class);
        MyBatisCommentRepository repository = new MyBatisCommentRepository(mapper);
        CommentDataObject root = row(ROOT_ID, null, 0, 7L);
        when(mapper.selectById(ROOT_ID)).thenReturn(root);
        when(mapper.selectByIdForUpdate(ROOT_ID)).thenAnswer(invocation -> {
            root.setStatus(1);
            root.setVersion(8L);
            return root;
        });

        assertThat(repository.lockReplyContext(POST_ID, ROOT_ID)).isEmpty();
        verify(mapper, never()).insert(any(CommentDataObject.class));
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
