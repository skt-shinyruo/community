package com.nowcoder.community.content.domain.model;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.content.exception.ContentErrorCode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentAggregateContractTest {

    private static final UUID COMMENT_ID = uuid(8101);
    private static final UUID AUTHOR_ID = uuid(8102);
    private static final UUID MODERATOR_ID = uuid(8103);
    private static final UUID POST_ID = uuid(8104);
    private static final Date CREATED_AT = new Date(1_000_000L);
    private static final long VERSION = 7L;

    @Test
    void snapshotShouldCarryAnExplicitMonotonicVersion() {
        assertThat(Arrays.stream(CommentSnapshot.class.getRecordComponents())
                .filter(component -> component.getName().equals("version"))
                .map(RecordComponent::getType))
                .containsExactly(long.class);
    }

    @Test
    void aggregateShouldNotExposePublicPersistenceSetters() {
        assertThat(Arrays.stream(Comment.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .filter(name -> name.startsWith("set")))
                .isEmpty();
    }

    @Test
    void aggregateShouldReconstituteAVersionedSnapshot() {
        Comment aggregate = reconstitute(snapshot(0, CREATED_AT, VERSION));

        assertThat(aggregate).isNotNull();
    }

    @Test
    void authorEditShouldAllowTheExactFifteenMinuteBoundaryAndCarryExpectedVersion() {
        Comment aggregate = reconstitute(snapshot(0, CREATED_AT, VERSION));
        Date exactBoundary = new Date(CREATED_AT.getTime() + 15L * 60L * 1000L);

        Object transition = invokeBehavior(
                aggregate,
                "editByAuthor",
                new Class<?>[]{UUID.class, UUID.class, String.class, Date.class},
                AUTHOR_ID,
                POST_ID,
                "edited content",
                exactBoundary
        );

        assertThat(transition.getClass().getSimpleName()).isEqualTo("CommentEdit");
        assertThat(accessor(transition, "commentId")).isEqualTo(COMMENT_ID);
        assertThat(accessor(transition, "expectedVersion")).isEqualTo(VERSION);
        assertThat(accessor(transition, "content")).isEqualTo("edited content");
        assertThat(accessor(transition, "updateTime")).isEqualTo(exactBoundary);
    }

    @Test
    void authorEditShouldRejectOneMillisecondPastTheWindow() {
        Comment aggregate = reconstitute(snapshot(0, CREATED_AT, VERSION));
        Date outsideWindow = new Date(CREATED_AT.getTime() + 15L * 60L * 1000L + 1L);

        assertThatThrownBy(() -> invokeBehavior(
                aggregate,
                "editByAuthor",
                new Class<?>[]{UUID.class, UUID.class, String.class, Date.class},
                AUTHOR_ID,
                POST_ID,
                "too late",
                outsideWindow
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void authorEditShouldRejectAnotherAuthorDeletedStateAndPostMismatch() {
        Comment active = reconstitute(snapshot(0, CREATED_AT, VERSION));
        Comment deleted = reconstitute(snapshot(1, CREATED_AT, VERSION));
        Date withinWindow = new Date(CREATED_AT.getTime() + 1L);

        assertThatThrownBy(() -> invokeBehavior(
                active,
                "editByAuthor",
                new Class<?>[]{UUID.class, UUID.class, String.class, Date.class},
                uuid(8199), POST_ID, "forbidden", withinWindow
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> invokeBehavior(
                deleted,
                "editByAuthor",
                new Class<?>[]{UUID.class, UUID.class, String.class, Date.class},
                AUTHOR_ID, POST_ID, "deleted", withinWindow
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ContentErrorCode.COMMENT_NOT_FOUND));
        assertThatThrownBy(() -> invokeBehavior(
                active,
                "editByAuthor",
                new Class<?>[]{UUID.class, UUID.class, String.class, Date.class},
                AUTHOR_ID, uuid(8198), "wrong post", withinWindow
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void authorAndModeratorDeletionShouldUseSeparatePermissionAwareAggregateBehaviors() {
        Date deletedAt = new Date(CREATED_AT.getTime() + 60_000L);
        Comment authorOwned = reconstitute(snapshot(0, CREATED_AT, VERSION));
        Comment moderated = reconstitute(snapshot(0, CREATED_AT, VERSION));

        Object authorDeletion = invokeBehavior(
                authorOwned,
                "deleteByAuthor",
                new Class<?>[]{UUID.class, UUID.class, String.class, Date.class},
                AUTHOR_ID, POST_ID, "author_delete", deletedAt
        );
        Object moderatorDeletion = invokeBehavior(
                moderated,
                "deleteByModerator",
                new Class<?>[]{UUID.class, String.class, Date.class},
                MODERATOR_ID, "hide: spam", deletedAt
        );

        assertDeletion(authorDeletion, AUTHOR_ID, "author_delete", deletedAt);
        assertDeletion(moderatorDeletion, MODERATOR_ID, "hide: spam", deletedAt);
        assertThatThrownBy(() -> invokeBehavior(
                reconstitute(snapshot(0, CREATED_AT, VERSION)),
                "deleteByAuthor",
                new Class<?>[]{UUID.class, UUID.class, String.class, Date.class},
                MODERATOR_ID, POST_ID, "author_delete", deletedAt
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> invokeBehavior(
                reconstitute(snapshot(0, CREATED_AT, VERSION)),
                "deleteByAuthor",
                new Class<?>[]{UUID.class, UUID.class, String.class, Date.class},
                AUTHOR_ID, uuid(8197), "author_delete", deletedAt
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> invokeBehavior(
                reconstitute(snapshot(1, CREATED_AT, VERSION)),
                "deleteByModerator",
                new Class<?>[]{UUID.class, String.class, Date.class},
                MODERATOR_ID, "hide: stale", deletedAt
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ContentErrorCode.COMMENT_NOT_FOUND));
    }

    private void assertDeletion(Object transition, UUID deletedBy, String reason, Date deletedAt) {
        assertThat(transition.getClass().getSimpleName()).isEqualTo("CommentDeletion");
        assertThat(accessor(transition, "commentId")).isEqualTo(COMMENT_ID);
        assertThat(accessor(transition, "expectedVersion")).isEqualTo(VERSION);
        assertThat(accessor(transition, "deletedBy")).isEqualTo(deletedBy);
        assertThat(accessor(transition, "deletedReason")).isEqualTo(reason);
        assertThat(accessor(transition, "deletedTime")).isEqualTo(deletedAt);
    }

    private Comment reconstitute(CommentSnapshot snapshot) {
        try {
            Method method = Comment.class.getDeclaredMethod("reconstitute", CommentSnapshot.class);
            assertThat(Modifier.isStatic(method.getModifiers())).isTrue();
            assertThat(Modifier.isPublic(method.getModifiers())).isTrue();
            return (Comment) invoke(method, null, snapshot);
        } catch (NoSuchMethodException error) {
            throw new AssertionError("Comment must expose public static reconstitute(CommentSnapshot)", error);
        }
    }

    private Object invokeBehavior(Comment aggregate, String name, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = Comment.class.getDeclaredMethod(name, parameterTypes);
            assertThat(Modifier.isPublic(method.getModifiers())).isTrue();
            return invoke(method, aggregate, arguments);
        } catch (NoSuchMethodException error) {
            throw new AssertionError("Comment must own aggregate behavior " + name, error);
        }
    }

    private Object accessor(Object target, String name) {
        try {
            return invoke(target.getClass().getMethod(name), target);
        } catch (NoSuchMethodException error) {
            throw new AssertionError(target.getClass().getSimpleName() + " must expose " + name + "()", error);
        }
    }

    private Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error invocationError) {
                throw invocationError;
            }
            throw new AssertionError("aggregate invocation failed", cause);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("aggregate invocation failed", error);
        }
    }

    private CommentSnapshot snapshot(int status, Date createTime, long version) {
        RecordComponent[] components = CommentSnapshot.class.getRecordComponents();
        Class<?>[] parameterTypes = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] values = Arrays.stream(components).map(component -> snapshotValue(component, status, createTime, version)).toArray();
        try {
            Constructor<CommentSnapshot> constructor = CommentSnapshot.class.getDeclaredConstructor(parameterTypes);
            return constructor.newInstance(values);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("CommentSnapshot must remain reconstructable as an immutable record", error);
        }
    }

    private Object snapshotValue(RecordComponent component, int status, Date createTime, long version) {
        return switch (component.getName()) {
            case "id" -> COMMENT_ID;
            case "userId" -> AUTHOR_ID;
            case "postId" -> POST_ID;
            case "rootCommentId" -> COMMENT_ID;
            case "parentCommentId", "replyToUserId", "updateTime" -> null;
            case "content" -> "original";
            case "status" -> status;
            case "createTime" -> createTime;
            case "editCount" -> 0;
            case "deletedBy" -> status == 0 ? null : MODERATOR_ID;
            case "deletedReason" -> status == 0 ? null : "deleted";
            case "deletedTime" -> status == 0 ? null : new Date(createTime.getTime() + 1L);
            case "version" -> version;
            default -> throw new AssertionError("Unhandled CommentSnapshot component: " + component.getName());
        };
    }
}
