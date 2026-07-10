package com.nowcoder.community.im.application;

import com.nowcoder.community.im.common.projection.UserBlockRelationSnapshot;
import com.nowcoder.community.im.common.projection.UserMessagingPolicySnapshot;
import com.nowcoder.community.im.common.policy.PrivateMessagePolicyDecision;
import com.nowcoder.community.social.api.model.SocialBlockRelationView;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImPolicySnapshotApplicationServiceTest {

    @Test
    void snapshotApplicationServiceShouldOnlyExposeOwnerDomainQueryApiConstructor() {
        assertThat(ImPolicySnapshotApplicationService.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        UserModerationQueryApi.class,
                        SocialBlockQueryApi.class,
                        UserLookupQueryApi.class
                ));
    }

    @Test
    void userPoliciesShouldProjectOwnerDomainModerationViews() {
        UserModerationQueryApi moderationQueryApi = mock(UserModerationQueryApi.class);
        SocialBlockQueryApi blockQueryApi = mock(SocialBlockQueryApi.class);
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        Instant now = Instant.now();
        Instant activeMuteUntil = now.plusSeconds(300);
        Instant activeBanUntil = now.plusSeconds(3600);
        Instant expiredMuteUntil = now.minusSeconds(2 * 24 * 3600);
        when(moderationQueryApi.currentModerationProjectionVersion()).thenReturn(909L);
        when(moderationQueryApi.scanModerationStatesAfterId(null, 2)).thenReturn(List.of(
                new UserModerationStateView(uuid(7), activeMuteUntil, null, 701L),
                new UserModerationStateView(uuid(8), expiredMuteUntil, activeBanUntil, 702L)
        ));
        when(moderationQueryApi.scanModerationStatesAfterId(uuid(8), 1)).thenReturn(List.of());

        ImPolicySnapshotApplicationService service = new ImPolicySnapshotApplicationService(
                moderationQueryApi,
                blockQueryApi,
                userLookupQueryApi
        );

        UserMessagingPolicySnapshot snapshot = service.userPolicies(null, 2);

        assertThat(snapshot.entries()).hasSize(2);
        assertThat(snapshot.entries().get(0).userId()).isEqualTo(uuid(7));
        assertThat(snapshot.entries().get(0).muted()).isTrue();
        assertThat(recordComponentValue(snapshot.entries().get(0), "muteUntil")).isEqualTo(activeMuteUntil.toEpochMilli());
        assertThat(recordComponentValue(snapshot.entries().get(0), "banUntil")).isNull();
        assertThat(snapshot.entries().get(0).canSendPrivate()).isFalse();
        assertThat(snapshot.entries().get(0).version()).isEqualTo(701L);
        assertThat(snapshot.entries().get(0).occurredAtEpochMillis()).isNotNull().isPositive();
        assertThat(snapshot.entries().get(1).userId()).isEqualTo(uuid(8));
        assertThat(snapshot.entries().get(1).suspended()).isTrue();
        assertThat(snapshot.entries().get(1).muted()).isFalse();
        assertThat(recordComponentValue(snapshot.entries().get(1), "muteUntil")).isEqualTo(expiredMuteUntil.toEpochMilli());
        assertThat(recordComponentValue(snapshot.entries().get(1), "banUntil")).isEqualTo(activeBanUntil.toEpochMilli());
        assertThat(snapshot.entries().get(1).canSendPrivate()).isFalse();
        assertThat(snapshot.entries().get(1).version()).isEqualTo(702L);
        assertThat(snapshot.hasMore()).isFalse();
        assertThat(snapshot.snapshotHighWatermark()).isEqualTo(909L);
    }

    @Test
    void blockRelationsShouldProjectOwnerDomainBlockViews() {
        UserModerationQueryApi moderationQueryApi = mock(UserModerationQueryApi.class);
        SocialBlockQueryApi blockQueryApi = mock(SocialBlockQueryApi.class);
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(blockQueryApi.currentBlockProjectionVersion()).thenReturn(808L);
        when(blockQueryApi.scanBlockRelationsAfter(null, null, 2)).thenReturn(List.of(
                new SocialBlockRelationView(uuid(1), uuid(2), 501L),
                new SocialBlockRelationView(uuid(1), uuid(3), 502L)
        ));
        when(blockQueryApi.scanBlockRelationsAfter(uuid(1), uuid(3), 1)).thenReturn(List.of());

        ImPolicySnapshotApplicationService service = new ImPolicySnapshotApplicationService(
                moderationQueryApi,
                blockQueryApi,
                userLookupQueryApi
        );

        UserBlockRelationSnapshot snapshot = service.blockRelations(null, null, 2);

        assertThat(snapshot.entries()).hasSize(2);
        assertThat(snapshot.entries().get(0).blockerUserId()).isEqualTo(uuid(1));
        assertThat(snapshot.entries().get(0).blockedUserId()).isEqualTo(uuid(2));
        assertThat(snapshot.entries().get(0).active()).isTrue();
        assertThat(snapshot.entries().get(0).version()).isEqualTo(501L);
        assertThat(snapshot.entries().get(1).blockerUserId()).isEqualTo(uuid(1));
        assertThat(snapshot.entries().get(1).blockedUserId()).isEqualTo(uuid(3));
        assertThat(snapshot.entries().get(1).active()).isTrue();
        assertThat(snapshot.entries().get(1).version()).isEqualTo(502L);
        assertThat(snapshot.hasMore()).isFalse();
        assertThat(snapshot.snapshotHighWatermark()).isEqualTo(808L);
    }

    @Test
    void emptySnapshotsShouldRetainZeroOwnerWatermarks() {
        UserModerationQueryApi moderationQueryApi = mock(UserModerationQueryApi.class);
        SocialBlockQueryApi blockQueryApi = mock(SocialBlockQueryApi.class);
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(moderationQueryApi.scanModerationStatesAfterId(null, 1)).thenReturn(List.of());
        when(blockQueryApi.scanBlockRelationsAfter(null, null, 1)).thenReturn(List.of());

        ImPolicySnapshotApplicationService service = new ImPolicySnapshotApplicationService(
                moderationQueryApi,
                blockQueryApi,
                userLookupQueryApi
        );

        assertThat(service.userPolicies(null, 1).snapshotHighWatermark()).isZero();
        assertThat(service.blockRelations(null, null, 1).snapshotHighWatermark()).isZero();
    }

    @Test
    void userPoliciesShouldRejectMissingOwnerStateAndIdentity() {
        UserModerationQueryApi moderationQueryApi = mock(UserModerationQueryApi.class);
        ImPolicySnapshotApplicationService service = new ImPolicySnapshotApplicationService(
                moderationQueryApi,
                mock(SocialBlockQueryApi.class),
                mock(UserLookupQueryApi.class)
        );
        when(moderationQueryApi.scanModerationStatesAfterId(null, 1))
                .thenReturn(Collections.singletonList(null));

        assertThatThrownBy(() -> service.userPolicies(null, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("moderation state");

        when(moderationQueryApi.scanModerationStatesAfterId(null, 1))
                .thenReturn(List.of(new UserModerationStateView(null, null, null, 1L)));

        assertThatThrownBy(() -> service.userPolicies(null, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void userPoliciesShouldRejectZeroOwnerVersionInsteadOfCreatingSnapshotEntry() {
        UserModerationQueryApi moderationQueryApi = mock(UserModerationQueryApi.class);
        when(moderationQueryApi.scanModerationStatesAfterId(null, 1))
                .thenReturn(List.of(new UserModerationStateView(uuid(7), null, null, 0L)));
        ImPolicySnapshotApplicationService service = new ImPolicySnapshotApplicationService(
                moderationQueryApi,
                mock(SocialBlockQueryApi.class),
                mock(UserLookupQueryApi.class)
        );

        assertThatThrownBy(() -> service.userPolicies(null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("version must be positive");
    }

    @Test
    void blockRelationsShouldRejectMissingOwnerStateAndIdentity() {
        SocialBlockQueryApi blockQueryApi = mock(SocialBlockQueryApi.class);
        ImPolicySnapshotApplicationService service = new ImPolicySnapshotApplicationService(
                mock(UserModerationQueryApi.class),
                blockQueryApi,
                mock(UserLookupQueryApi.class)
        );
        when(blockQueryApi.scanBlockRelationsAfter(null, null, 1))
                .thenReturn(Collections.singletonList(null));

        assertThatThrownBy(() -> service.blockRelations(null, null, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("block snapshot relation");

        when(blockQueryApi.scanBlockRelationsAfter(null, null, 1))
                .thenReturn(List.of(new SocialBlockRelationView(null, uuid(2), 1L)));

        assertThatThrownBy(() -> service.blockRelations(null, null, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blockerUserId");
    }

    @Test
    void blockRelationsShouldRejectZeroOwnerVersionInsteadOfCreatingSnapshotEntry() {
        SocialBlockQueryApi blockQueryApi = mock(SocialBlockQueryApi.class);
        when(blockQueryApi.scanBlockRelationsAfter(null, null, 1))
                .thenReturn(List.of(new SocialBlockRelationView(uuid(1), uuid(2), 0L)));
        ImPolicySnapshotApplicationService service = new ImPolicySnapshotApplicationService(
                mock(UserModerationQueryApi.class),
                blockQueryApi,
                mock(UserLookupQueryApi.class)
        );

        assertThatThrownBy(() -> service.blockRelations(null, null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("version must be positive");
    }

    @Test
    void privateMessageDecisionShouldRejectMutedSenderFromOwnerState() {
        UserModerationQueryApi moderationQueryApi = mock(UserModerationQueryApi.class);
        SocialBlockQueryApi blockQueryApi = mock(SocialBlockQueryApi.class);
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.getSummaryById(uuid(1))).thenReturn(summary(uuid(1)));
        when(userLookupQueryApi.getSummaryById(uuid(2))).thenReturn(summary(uuid(2)));
        when(moderationQueryApi.getModerationState(uuid(1)))
                .thenReturn(new UserModerationStateView(uuid(1), Instant.now().plusSeconds(60), null, 1L));
        when(moderationQueryApi.getModerationState(uuid(2)))
                .thenReturn(new UserModerationStateView(uuid(2), null, null, 2L));

        ImPolicySnapshotApplicationService service = new ImPolicySnapshotApplicationService(
                moderationQueryApi,
                blockQueryApi,
                userLookupQueryApi
        );

        PrivateMessagePolicyDecision decision = service.decidePrivateMessage(uuid(1), uuid(2));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo(403);
        assertThat(decision.reasonCode()).isEqualTo("policy_denied");
        assertThat(decision.message()).isEqualTo("发送方无权限发送私信");
    }

    @Test
    void privateMessageDecisionShouldRejectBlockedUsersFromOwnerState() {
        UserModerationQueryApi moderationQueryApi = mock(UserModerationQueryApi.class);
        SocialBlockQueryApi blockQueryApi = mock(SocialBlockQueryApi.class);
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.getSummaryById(uuid(1))).thenReturn(summary(uuid(1)));
        when(userLookupQueryApi.getSummaryById(uuid(2))).thenReturn(summary(uuid(2)));
        when(moderationQueryApi.getModerationState(uuid(1)))
                .thenReturn(new UserModerationStateView(uuid(1), null, null, 1L));
        when(moderationQueryApi.getModerationState(uuid(2)))
                .thenReturn(new UserModerationStateView(uuid(2), null, null, 2L));
        when(blockQueryApi.isEitherBlocked(uuid(1), uuid(2))).thenReturn(true);

        ImPolicySnapshotApplicationService service = new ImPolicySnapshotApplicationService(
                moderationQueryApi,
                blockQueryApi,
                userLookupQueryApi
        );

        PrivateMessagePolicyDecision decision = service.decidePrivateMessage(uuid(1), uuid(2));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo(403);
        assertThat(decision.reasonCode()).isEqualTo("policy_denied");
        assertThat(decision.message()).isEqualTo("用户已拉黑");
    }

    @Test
    void privateMessageDecisionShouldRejectMissingTargetUserFromOwnerState() {
        UserModerationQueryApi moderationQueryApi = mock(UserModerationQueryApi.class);
        SocialBlockQueryApi blockQueryApi = mock(SocialBlockQueryApi.class);
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.getSummaryById(uuid(1))).thenReturn(summary(uuid(1)));
        when(userLookupQueryApi.getSummaryById(uuid(2))).thenReturn(null);

        ImPolicySnapshotApplicationService service = new ImPolicySnapshotApplicationService(
                moderationQueryApi,
                blockQueryApi,
                userLookupQueryApi
        );

        PrivateMessagePolicyDecision decision = service.decidePrivateMessage(uuid(1), uuid(2));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo(404);
        assertThat(decision.reasonCode()).isEqualTo("policy_denied");
        assertThat(decision.message()).isEqualTo("接收方不存在");
    }

    private static Object recordComponentValue(Object record, String componentName) {
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            if (component.getName().equals(componentName)) {
                try {
                    return component.getAccessor().invoke(record);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("cannot read component: " + componentName, e);
                }
            }
        }
        throw new AssertionError(record.getClass().getSimpleName() + " missing component: " + componentName);
    }

    private static UserSummaryView summary(java.util.UUID userId) {
        return new UserSummaryView(userId, "u-" + userId, "", 0);
    }
}
