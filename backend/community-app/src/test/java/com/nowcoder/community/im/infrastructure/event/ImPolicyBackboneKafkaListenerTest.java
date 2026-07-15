package com.nowcoder.community.im.infrastructure.event;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.im.application.ImPolicyProjectionApplicationService;
import com.nowcoder.community.im.application.command.ProjectBlockRelationCommand;
import com.nowcoder.community.im.application.command.ProjectUserPolicyCommand;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialContractEventCodec;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.social.infrastructure.event.JacksonSocialContractEventCodec;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserContractEventCodec;
import com.nowcoder.community.user.contracts.event.UserEventTypes;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import com.nowcoder.community.user.infrastructure.event.JacksonUserContractEventCodec;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ImPolicyBackboneKafkaListenerTest {

    private final JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
    private final UserContractEventCodec userContractEventCodec = new JacksonUserContractEventCodec(jsonCodec);
    private final SocialContractEventCodec socialContractEventCodec = new JacksonSocialContractEventCodec(jsonCodec);

    @Test
    void shouldProjectUserPolicyFromUserBackbone() {
        ImPolicyProjectionApplicationService applicationService = mock(ImPolicyProjectionApplicationService.class);
        ImPolicyBackboneKafkaListener listener = listener(applicationService);
        UserPolicyChangedPayload payload = userPolicyPayload();

        listener.onUserEvent(new UserContractEvent(
                "user-event-1", UserEventTypes.USER_POLICY_CHANGED, jsonCodec.valueToTree(payload)));

        verify(applicationService).projectUserPolicy(new ProjectUserPolicyCommand(
                "user", "user-event-1", uuid(7), true, false, true,
                1712345678901L, 1712355678901L, false,
                1712345678901L, 777L
        ));
    }

    @Test
    void shouldProjectUserPolicyWhenKafkaPayloadIsMap() {
        ImPolicyProjectionApplicationService applicationService = mock(ImPolicyProjectionApplicationService.class);
        ImPolicyBackboneKafkaListener listener = listener(applicationService);

        listener.onUserEvent(new UserContractEvent(
                "user-event-map", UserEventTypes.USER_POLICY_CHANGED,
                jsonCodec.valueToTree(Map.of(
                        "userId", uuid(7).toString(),
                        "userExists", true,
                        "occurredAtEpochMillis", 1712345678901L,
                        "version", 777L
                ))));

        verify(applicationService).projectUserPolicy(new ProjectUserPolicyCommand(
                "user", "user-event-map", uuid(7), true, false, false,
                null, null, false, 1712345678901L, 777L
        ));
    }

    @Test
    void shouldProjectBlockRelationUsingTopLevelSocialMetadata() {
        ImPolicyProjectionApplicationService applicationService = mock(ImPolicyProjectionApplicationService.class);
        ImPolicyBackboneKafkaListener listener = listener(applicationService);
        Instant occurredAt = Instant.parse("2026-07-10T01:02:03Z");
        BlockPayload payload = new BlockPayload();
        payload.setBlockerUserId(uuid(11));
        payload.setBlockedUserId(uuid(22));
        payload.setBlocked(Boolean.TRUE);

        listener.onSocialEvent(new SocialContractEvent(
                "social-event-1", uuid(11), "user", SocialEventTypes.BLOCK_RELATION_CHANGED,
                occurredAt, 888L, jsonCodec.valueToTree(payload)));

        verify(applicationService).projectBlockRelation(new ProjectBlockRelationCommand(
                "social", "social-event-1", uuid(11), uuid(22), true,
                occurredAt.toEpochMilli(), 888L
        ));
    }

    @Test
    void shouldProjectBlockRelationWhenKafkaPayloadIsMap() {
        ImPolicyProjectionApplicationService applicationService = mock(ImPolicyProjectionApplicationService.class);
        ImPolicyBackboneKafkaListener listener = listener(applicationService);
        Instant occurredAt = Instant.parse("2026-07-10T01:02:03Z");

        listener.onSocialEvent(new SocialContractEvent(
                "social-event-map", null, null, SocialEventTypes.BLOCK_RELATION_CHANGED,
                occurredAt, 888L, jsonCodec.valueToTree(Map.of(
                        "blockerUserId", uuid(11).toString(),
                        "blockedUserId", uuid(22).toString(),
                        "blocked", true
                ))));

        verify(applicationService).projectBlockRelation(new ProjectBlockRelationCommand(
                "social", "social-event-map", uuid(11), uuid(22), true,
                occurredAt.toEpochMilli(), 888L
        ));
    }

    @Test
    void recognizedEventsWithInvalidSourceOrIdentityShouldFailDelivery() {
        ImPolicyProjectionApplicationService applicationService = mock(ImPolicyProjectionApplicationService.class);
        ImPolicyBackboneKafkaListener listener = listener(applicationService);

        UserPolicyChangedPayload userPayload = userPolicyPayload();
        assertThatThrownBy(() -> listener.onUserEvent(new UserContractEvent(
                " ", UserEventTypes.USER_POLICY_CHANGED, jsonCodec.valueToTree(userPayload))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(UserEventTypes.USER_POLICY_CHANGED);

        assertThatThrownBy(() -> listener.onUserEvent(new UserContractEvent(
                "user-missing-version", UserEventTypes.USER_POLICY_CHANGED,
                jsonCodec.valueToTree(Map.of(
                        "userId", uuid(7).toString(), "occurredAtEpochMillis", 1712345678901L)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user-missing-version");

        assertThatThrownBy(() -> listener.onSocialEvent(new SocialContractEvent(
                "social-missing-time", uuid(11), "user", SocialEventTypes.BLOCK_RELATION_CHANGED,
                null, 888L, jsonCodec.valueToTree(new BlockPayload()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("social-missing-time");

        verifyNoInteractions(applicationService);
    }

    @Test
    void unsupportedEventsShouldBeIgnored() {
        ImPolicyProjectionApplicationService applicationService = mock(ImPolicyProjectionApplicationService.class);
        ImPolicyBackboneKafkaListener listener = listener(applicationService);

        listener.onUserEvent(new UserContractEvent(
                "user-follow", "UserRegistered", JsonMappers.standard().createObjectNode()));
        listener.onSocialEvent(new SocialContractEvent(
                "social-follow", null, null, SocialEventTypes.FOLLOW_CREATED,
                Instant.EPOCH, 1L, JsonMappers.standard().createObjectNode()));
        listener.onUserEvent(null);
        listener.onSocialEvent(null);

        verifyNoInteractions(applicationService);
    }

    private ImPolicyBackboneKafkaListener listener(
            ImPolicyProjectionApplicationService applicationService
    ) {
        return new ImPolicyBackboneKafkaListener(
                applicationService, userContractEventCodec, socialContractEventCodec);
    }

    private static UserPolicyChangedPayload userPolicyPayload() {
        UserPolicyChangedPayload payload = new UserPolicyChangedPayload();
        payload.setUserId(uuid(7));
        payload.setUserExists(true);
        payload.setMuted(true);
        payload.setMuteUntil(1712345678901L);
        payload.setBanUntil(1712355678901L);
        payload.setCanSendPrivate(false);
        payload.setOccurredAtEpochMillis(1712345678901L);
        payload.setVersion(777L);
        return payload;
    }
}
