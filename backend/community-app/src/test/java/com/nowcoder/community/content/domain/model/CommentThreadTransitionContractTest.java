package com.nowcoder.community.content.domain.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentThreadTransitionContractTest {

    private static final UUID ROOT_ID = uuid(8201);
    private static final UUID REPLY_ID = uuid(8202);
    private static final UUID SECOND_REPLY_ID = uuid(8204);
    private static final UUID ACTOR_ID = uuid(8203);
    private static final Date DELETED_AT = new Date(2_000_000L);

    @Test
    void singleReplyDeletionShouldCarryTheExpectedVersionAndDeletionFact() {
        Class<?> deletionType = requireClass("com.nowcoder.community.content.domain.model.CommentDeletion");
        assertRecordComponents(
                deletionType,
                "commentId", "expectedVersion", "deletedBy", "deletedReason", "deletedTime"
        );

        Object deletion = instantiateRecord(deletionType, Map.of(
                "commentId", REPLY_ID,
                "expectedVersion", 11L,
                "deletedBy", ACTOR_ID,
                "deletedReason", "author_delete",
                "deletedTime", DELETED_AT
        ));

        assertThat(accessor(deletion, "commentId")).isEqualTo(REPLY_ID);
        assertThat(accessor(deletion, "expectedVersion")).isEqualTo(11L);
        assertThat(accessor(deletion, "deletedBy")).isEqualTo(ACTOR_ID);
        assertThat(accessor(deletion, "deletedReason")).isEqualTo("author_delete");
        assertThat(accessor(deletion, "deletedTime")).isEqualTo(DELETED_AT);
    }

    @Test
    void rootThreadDeletionShouldFreezeRootFirstOrderedVersionTargets() {
        Class<?> threadType = requireClass("com.nowcoder.community.content.domain.model.CommentThreadDeletion");
        assertRecordComponents(
                threadType,
                "rootCommentId", "targets", "deletedBy", "deletedReason", "deletedTime"
        );
        Class<?> targetType = targetType(threadType);
        assertRecordComponents(targetType, "commentId", "expectedVersion");
        Object root = target(targetType, ROOT_ID, 7L);
        Object reply = target(targetType, REPLY_ID, 9L);
        List<Object> mutableTargets = new ArrayList<>(List.of(root, reply));

        Object transition = threadDeletion(threadType, mutableTargets);
        mutableTargets.clear();
        @SuppressWarnings("unchecked")
        List<Object> frozenTargets = (List<Object>) accessor(transition, "targets");

        assertThat(accessor(transition, "rootCommentId")).isEqualTo(ROOT_ID);
        assertThat(frozenTargets).containsExactly(root, reply);
        assertThatThrownBy(() -> frozenTargets.add(reply))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void threadDeletionShouldRejectDuplicateTargetsAndAReplyBeforeTheRoot() {
        Class<?> threadType = requireClass("com.nowcoder.community.content.domain.model.CommentThreadDeletion");
        Class<?> targetType = targetType(threadType);
        Object root = target(targetType, ROOT_ID, 7L);
        Object reply = target(targetType, REPLY_ID, 9L);
        Object duplicateRoot = target(targetType, ROOT_ID, 8L);

        assertThatThrownBy(() -> threadDeletion(threadType, List.of(root, duplicateRoot)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target");
        assertThatThrownBy(() -> threadDeletion(threadType, List.of(reply, root)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("root");
    }

    @Test
    void threadFactoryShouldOrderRootThenRepliesByCreateTimeAndId() {
        Date sameReplyTime = new Date(1_500_000L);
        CommentDeletion rootDeletion = new CommentDeletion(
                ROOT_ID, 7L, ACTOR_ID, "author_delete", DELETED_AT
        );

        CommentThreadDeletion transition = CommentThreadDeletion.from(
                rootDeletion,
                List.of(
                        snapshot(SECOND_REPLY_ID, ROOT_ID, sameReplyTime, 10L),
                        snapshot(ROOT_ID, null, new Date(2_000_000L), 12L),
                        snapshot(REPLY_ID, ROOT_ID, sameReplyTime, 9L)
                )
        );

        assertThat(transition.targets())
                .extracting(CommentThreadDeletion.Target::commentId)
                .containsExactly(ROOT_ID, REPLY_ID, SECOND_REPLY_ID);
        assertThat(transition.targets())
                .extracting(CommentThreadDeletion.Target::expectedVersion)
                .containsExactly(7L, 9L, 10L);
    }

    private Object threadDeletion(Class<?> threadType, List<?> targets) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("rootCommentId", ROOT_ID);
        values.put("targets", targets);
        values.put("deletedBy", ACTOR_ID);
        values.put("deletedReason", "author_delete");
        values.put("deletedTime", DELETED_AT);
        return instantiateRecord(threadType, values);
    }

    private Object target(Class<?> targetType, UUID commentId, long expectedVersion) {
        return instantiateRecord(targetType, Map.of(
                "commentId", commentId,
                "expectedVersion", expectedVersion
        ));
    }

    private Class<?> targetType(Class<?> threadType) {
        RecordComponent targets = Arrays.stream(threadType.getRecordComponents())
                .filter(component -> component.getName().equals("targets"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("CommentThreadDeletion must expose targets"));
        Type genericType = targets.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)
                || parameterizedType.getActualTypeArguments().length != 1
                || !(parameterizedType.getActualTypeArguments()[0] instanceof Class<?> elementType)) {
            throw new AssertionError("CommentThreadDeletion.targets must be List<CommentId/expectedVersion pair>");
        }
        return elementType;
    }

    private void assertRecordComponents(Class<?> type, String... expectedNames) {
        assertThat(type.isRecord()).as(type.getSimpleName() + " must be immutable").isTrue();
        assertThat(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly(expectedNames);
    }

    private Object instantiateRecord(Class<?> type, Map<String, Object> values) {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] parameterTypes = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] arguments = Arrays.stream(components).map(component -> {
            if (!values.containsKey(component.getName())) {
                throw new AssertionError("Unhandled " + type.getSimpleName() + " component: " + component.getName());
            }
            return values.get(component.getName());
        }).toArray();
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError("transition construction failed", cause);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("transition construction failed", error);
        }
    }

    private Object accessor(Object target, String name) {
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(target.getClass().getSimpleName() + " must expose " + name + "()", error);
        }
    }

    private CommentSnapshot snapshot(UUID id, UUID parentId, Date createTime, long version) {
        return new CommentSnapshot(
                id,
                ACTOR_ID,
                uuid(8299),
                parentId == null ? id : ROOT_ID,
                parentId,
                parentId == null ? null : ACTOR_ID,
                "comment",
                0,
                createTime,
                null,
                0,
                null,
                null,
                null,
                version
        );
    }

    private Class<?> requireClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException error) {
            throw new AssertionError("Missing required Comment transition: " + className, error);
        }
    }
}
