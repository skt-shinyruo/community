package com.nowcoder.community.im.application;

import com.nowcoder.community.im.common.policy.PrivateMessagePolicyDecision;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImPrivateMessageDecisionApplicationServiceTest {

    private static final Instant TEST_NOW = Instant.parse("2026-08-10T00:00:00Z");
    private static final Clock TEST_CLOCK = Clock.fixed(TEST_NOW, ZoneOffset.UTC);

    @Test
    void decisionApplicationServiceShouldOnlyExposeOwnerDomainQueryApiConstructor() {
        assertThat(ImPrivateMessageDecisionApplicationService.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        UserModerationQueryApi.class,
                        SocialBlockQueryApi.class,
                        UserLookupQueryApi.class,
                        Clock.class
                ));
    }

    @Test
    void privateMessageDecisionShouldRejectInvalidIdentity() {
        ImPrivateMessageDecisionApplicationService service = service(
                mock(UserModerationQueryApi.class),
                mock(SocialBlockQueryApi.class),
                mock(UserLookupQueryApi.class)
        );

        PrivateMessagePolicyDecision nullTarget = service.decidePrivateMessage(uuid(1), null);
        PrivateMessagePolicyDecision selfChat = service.decidePrivateMessage(uuid(1), uuid(1));

        assertThat(nullTarget.allowed()).isFalse();
        assertThat(nullTarget.code()).isEqualTo(400);
        assertThat(nullTarget.reasonCode()).isEqualTo("invalid_request");
        assertThat(selfChat.allowed()).isFalse();
        assertThat(selfChat.code()).isEqualTo(400);
        assertThat(selfChat.reasonCode()).isEqualTo("invalid_request");
    }

    @Test
    void privateMessageDecisionShouldRejectMissingSenderFromOwnerState() {
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.getSummaryById(uuid(1))).thenReturn(null);
        ImPrivateMessageDecisionApplicationService service = service(
                mock(UserModerationQueryApi.class),
                mock(SocialBlockQueryApi.class),
                userLookupQueryApi
        );

        PrivateMessagePolicyDecision decision = service.decidePrivateMessage(uuid(1), uuid(2));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo(404);
        assertThat(decision.reasonCode()).isEqualTo("policy_denied");
        assertThat(decision.message()).isEqualTo("发送方不存在");
    }

    @Test
    void privateMessageDecisionShouldRejectMissingTargetUserFromOwnerState() {
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.getSummaryById(uuid(1))).thenReturn(summary(uuid(1)));
        when(userLookupQueryApi.getSummaryById(uuid(2))).thenReturn(null);
        ImPrivateMessageDecisionApplicationService service = service(
                mock(UserModerationQueryApi.class),
                mock(SocialBlockQueryApi.class),
                userLookupQueryApi
        );

        PrivateMessagePolicyDecision decision = service.decidePrivateMessage(uuid(1), uuid(2));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo(404);
        assertThat(decision.reasonCode()).isEqualTo("policy_denied");
        assertThat(decision.message()).isEqualTo("接收方不存在");
    }

    @Test
    void privateMessageDecisionShouldRejectMutedSenderFromOwnerState() {
        UserModerationQueryApi moderationQueryApi = mock(UserModerationQueryApi.class);
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.getSummaryById(uuid(1))).thenReturn(summary(uuid(1)));
        when(userLookupQueryApi.getSummaryById(uuid(2))).thenReturn(summary(uuid(2)));
        when(moderationQueryApi.getModerationState(uuid(1)))
                .thenReturn(new UserModerationStateView(uuid(1), TEST_NOW.plusSeconds(60), null, 1L));
        when(moderationQueryApi.getModerationState(uuid(2)))
                .thenReturn(new UserModerationStateView(uuid(2), null, null, 2L));

        ImPrivateMessageDecisionApplicationService service = service(
                moderationQueryApi,
                mock(SocialBlockQueryApi.class),
                userLookupQueryApi
        );

        PrivateMessagePolicyDecision decision = service.decidePrivateMessage(uuid(1), uuid(2));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo(403);
        assertThat(decision.reasonCode()).isEqualTo("policy_denied");
        assertThat(decision.message()).isEqualTo("发送方无权限发送私信");
    }

    @Test
    void privateMessageDecisionShouldRejectSuspendedSenderFromOwnerState() {
        UserModerationQueryApi moderationQueryApi = mock(UserModerationQueryApi.class);
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.getSummaryById(uuid(1))).thenReturn(summary(uuid(1)));
        when(userLookupQueryApi.getSummaryById(uuid(2))).thenReturn(summary(uuid(2)));
        when(moderationQueryApi.getModerationState(uuid(1)))
                .thenReturn(new UserModerationStateView(uuid(1), null, TEST_NOW.plusSeconds(3600), 1L));
        when(moderationQueryApi.getModerationState(uuid(2)))
                .thenReturn(new UserModerationStateView(uuid(2), null, null, 2L));

        ImPrivateMessageDecisionApplicationService service = service(
                moderationQueryApi,
                mock(SocialBlockQueryApi.class),
                userLookupQueryApi
        );

        PrivateMessagePolicyDecision decision = service.decidePrivateMessage(uuid(1), uuid(2));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo(403);
        assertThat(decision.reasonCode()).isEqualTo("policy_denied");
        assertThat(decision.message()).isEqualTo("发送方无权限发送私信");
    }

    @Test
    void privateMessageDecisionShouldRejectMutedRecipientFromOwnerState() {
        UserModerationQueryApi moderationQueryApi = mock(UserModerationQueryApi.class);
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.getSummaryById(uuid(1))).thenReturn(summary(uuid(1)));
        when(userLookupQueryApi.getSummaryById(uuid(2))).thenReturn(summary(uuid(2)));
        when(moderationQueryApi.getModerationState(uuid(1)))
                .thenReturn(new UserModerationStateView(uuid(1), null, null, 1L));
        when(moderationQueryApi.getModerationState(uuid(2)))
                .thenReturn(new UserModerationStateView(uuid(2), TEST_NOW.plusSeconds(60), null, 2L));

        ImPrivateMessageDecisionApplicationService service = service(
                moderationQueryApi,
                mock(SocialBlockQueryApi.class),
                userLookupQueryApi
        );

        PrivateMessagePolicyDecision decision = service.decidePrivateMessage(uuid(1), uuid(2));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo(403);
        assertThat(decision.reasonCode()).isEqualTo("policy_denied");
        assertThat(decision.message()).isEqualTo("接收方不允许私信");
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

        ImPrivateMessageDecisionApplicationService service = service(
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
    void privateMessageDecisionShouldAllowCleanPairFromOwnerState() {
        UserModerationQueryApi moderationQueryApi = mock(UserModerationQueryApi.class);
        SocialBlockQueryApi blockQueryApi = mock(SocialBlockQueryApi.class);
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.getSummaryById(uuid(1))).thenReturn(summary(uuid(1)));
        when(userLookupQueryApi.getSummaryById(uuid(2))).thenReturn(summary(uuid(2)));
        when(moderationQueryApi.getModerationState(uuid(1)))
                .thenReturn(new UserModerationStateView(uuid(1), TEST_NOW.minusSeconds(60), null, 1L));
        when(moderationQueryApi.getModerationState(uuid(2)))
                .thenReturn(new UserModerationStateView(uuid(2), null, null, 2L));
        when(blockQueryApi.isEitherBlocked(uuid(1), uuid(2))).thenReturn(false);

        ImPrivateMessageDecisionApplicationService service = service(
                moderationQueryApi,
                blockQueryApi,
                userLookupQueryApi
        );

        PrivateMessagePolicyDecision decision = service.decidePrivateMessage(uuid(1), uuid(2));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.code()).isZero();
        assertThat(decision.reasonCode()).isEqualTo("allowed");
    }

    private static UserSummaryView summary(UUID userId) {
        return new UserSummaryView(userId, "u-" + userId, "", 0);
    }

    private static ImPrivateMessageDecisionApplicationService service(
            UserModerationQueryApi moderationQueryApi,
            SocialBlockQueryApi blockQueryApi,
            UserLookupQueryApi userLookupQueryApi
    ) {
        return new ImPrivateMessageDecisionApplicationService(
                moderationQueryApi,
                blockQueryApi,
                userLookupQueryApi,
                TEST_CLOCK
        );
    }
}
