package com.nowcoder.community.im.application;

import com.nowcoder.community.im.common.projection.UserBlockRelationSnapshot;
import com.nowcoder.community.im.common.projection.UserMessagingPolicySnapshot;
import com.nowcoder.community.social.api.model.SocialBlockRelationView;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.List;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImPolicySnapshotApplicationServiceTest {

    @Test
    void snapshotApplicationServiceShouldOnlyExposeOwnerDomainQueryApiConstructor() {
        assertThat(ImPolicySnapshotApplicationService.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        UserModerationQueryApi.class,
                        SocialBlockQueryApi.class
                ));
    }

    @Test
    void userPoliciesShouldProjectOwnerDomainModerationViews() {
        UserModerationQueryApi moderationQueryApi = mock(UserModerationQueryApi.class);
        SocialBlockQueryApi blockQueryApi = mock(SocialBlockQueryApi.class);
        Instant now = Instant.now();
        Instant activeMuteUntil = now.plusSeconds(300);
        Instant activeBanUntil = now.plusSeconds(3600);
        Instant expiredMuteUntil = now.minusSeconds(2 * 24 * 3600);
        when(moderationQueryApi.scanModerationStatesAfterId(null, 2)).thenReturn(List.of(
                new UserModerationStateView(uuid(7), activeMuteUntil, null),
                new UserModerationStateView(uuid(8), expiredMuteUntil, activeBanUntil)
        ));
        when(moderationQueryApi.scanModerationStatesAfterId(uuid(8), 1)).thenReturn(List.of());

        ImPolicySnapshotApplicationService service = new ImPolicySnapshotApplicationService(moderationQueryApi, blockQueryApi);

        UserMessagingPolicySnapshot snapshot = service.userPolicies(null, 2);

        assertThat(snapshot.entries()).hasSize(2);
        assertThat(snapshot.entries().get(0).userId()).isEqualTo(uuid(7));
        assertThat(snapshot.entries().get(0).muted()).isTrue();
        assertThat(recordComponentValue(snapshot.entries().get(0), "muteUntil")).isEqualTo(activeMuteUntil.toEpochMilli());
        assertThat(recordComponentValue(snapshot.entries().get(0), "banUntil")).isNull();
        assertThat(snapshot.entries().get(0).canSendPrivate()).isFalse();
        assertThat(snapshot.entries().get(1).userId()).isEqualTo(uuid(8));
        assertThat(snapshot.entries().get(1).suspended()).isTrue();
        assertThat(snapshot.entries().get(1).muted()).isFalse();
        assertThat(recordComponentValue(snapshot.entries().get(1), "muteUntil")).isEqualTo(expiredMuteUntil.toEpochMilli());
        assertThat(recordComponentValue(snapshot.entries().get(1), "banUntil")).isEqualTo(activeBanUntil.toEpochMilli());
        assertThat(snapshot.entries().get(1).canSendPrivate()).isFalse();
        assertThat(snapshot.hasMore()).isFalse();
    }

    @Test
    void blockRelationsShouldProjectOwnerDomainBlockViews() {
        UserModerationQueryApi moderationQueryApi = mock(UserModerationQueryApi.class);
        SocialBlockQueryApi blockQueryApi = mock(SocialBlockQueryApi.class);
        when(blockQueryApi.scanBlockRelationsAfter(null, null, 2)).thenReturn(List.of(
                new SocialBlockRelationView(uuid(1), uuid(2)),
                new SocialBlockRelationView(uuid(1), uuid(3))
        ));
        when(blockQueryApi.scanBlockRelationsAfter(uuid(1), uuid(3), 1)).thenReturn(List.of());

        ImPolicySnapshotApplicationService service = new ImPolicySnapshotApplicationService(moderationQueryApi, blockQueryApi);

        UserBlockRelationSnapshot snapshot = service.blockRelations(null, null, 2);

        assertThat(snapshot.entries()).hasSize(2);
        assertThat(snapshot.entries().get(0).blockerUserId()).isEqualTo(uuid(1));
        assertThat(snapshot.entries().get(0).blockedUserId()).isEqualTo(uuid(2));
        assertThat(snapshot.entries().get(0).active()).isTrue();
        assertThat(snapshot.entries().get(1).blockerUserId()).isEqualTo(uuid(1));
        assertThat(snapshot.entries().get(1).blockedUserId()).isEqualTo(uuid(3));
        assertThat(snapshot.entries().get(1).active()).isTrue();
        assertThat(snapshot.hasMore()).isFalse();
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
}
