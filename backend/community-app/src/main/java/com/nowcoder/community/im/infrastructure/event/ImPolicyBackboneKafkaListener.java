package com.nowcoder.community.im.infrastructure.event;

import com.nowcoder.community.im.application.ImPolicyProjectionApplicationService;
import com.nowcoder.community.im.application.command.ProjectBlockRelationCommand;
import com.nowcoder.community.im.application.command.ProjectUserPolicyCommand;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialContractEventCodec;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.social.contracts.event.SocialTypedEvent;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserContractEventCodec;
import com.nowcoder.community.user.contracts.event.UserEventTypes;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import com.nowcoder.community.user.contracts.event.UserTypedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Component
public class ImPolicyBackboneKafkaListener {

    private final ImPolicyProjectionApplicationService projectionApplicationService;
    private final UserContractEventCodec userContractEventCodec;
    private final SocialContractEventCodec socialContractEventCodec;

    public ImPolicyBackboneKafkaListener(
            ImPolicyProjectionApplicationService projectionApplicationService,
            UserContractEventCodec userContractEventCodec,
            SocialContractEventCodec socialContractEventCodec
    ) {
        this.projectionApplicationService = projectionApplicationService;
        this.userContractEventCodec = userContractEventCodec;
        this.socialContractEventCodec = socialContractEventCodec;
    }

    @KafkaListener(
            topics = "${user.events.kafka-topic:user.events}",
            groupId = "${im.policy.kafka.consumer.group-id:im-policy-projection}",
            concurrency = "${im.policy.kafka.consumer.concurrency:3}"
    )
    public void onUserEvent(UserContractEvent event) {
        if (event == null || !UserEventTypes.USER_POLICY_CHANGED.equals(event.type())) {
            return;
        }
        UserPolicyChangedPayload payload;
        try {
            payload = ((UserTypedEvent.UserPolicyChanged) userContractEventCodec.decode(event)).payload();
        } catch (RuntimeException error) {
            throw malformed(event.type(), event.eventId());
        }
        if (!StringUtils.hasText(event.eventId())
                || payload == null
                || payload.getUserId() == null
                || payload.getOccurredAtEpochMillis() <= 0L
                || payload.getVersion() == null
                || payload.getVersion() <= 0L) {
            throw malformed(event.type(), event.eventId());
        }
        projectionApplicationService.projectUserPolicy(new ProjectUserPolicyCommand(
                "user",
                event.eventId(),
                payload.getUserId(),
                payload.isUserExists(),
                payload.isSuspended(),
                payload.isMuted(),
                payload.getMuteUntil(),
                payload.getBanUntil(),
                payload.isCanSendPrivate(),
                payload.getOccurredAtEpochMillis(),
                payload.getVersion()
        ));
    }

    @KafkaListener(
            topics = "${social.events.kafka-topic:social.events}",
            groupId = "${im.policy.kafka.consumer.group-id:im-policy-projection}",
            concurrency = "${im.policy.kafka.consumer.concurrency:3}"
    )
    public void onSocialEvent(SocialContractEvent event) {
        if (event == null || !SocialEventTypes.BLOCK_RELATION_CHANGED.equals(event.type())) {
            return;
        }
        BlockPayload payload;
        try {
            payload = ((SocialTypedEvent.BlockRelationChanged) socialContractEventCodec.decode(event)).payload();
        } catch (RuntimeException error) {
            throw malformed(event.type(), event.eventId());
        }
        if (!StringUtils.hasText(event.eventId())
                || event.occurredAt() == null
                || event.version() <= 0L
                || payload == null
                || payload.getBlockerUserId() == null
                || payload.getBlockedUserId() == null
                || payload.getBlocked() == null) {
            throw malformed(event.type(), event.eventId());
        }
        projectionApplicationService.projectBlockRelation(new ProjectBlockRelationCommand(
                "social",
                event.eventId(),
                payload.getBlockerUserId(),
                payload.getBlockedUserId(),
                payload.getBlocked(),
                event.occurredAt().toEpochMilli(),
                event.version()
        ));
    }

    private IllegalArgumentException malformed(String type, String eventId) {
        return new IllegalArgumentException(
                "invalid recognized IM policy event: type=" + type + ", eventId=" + eventId);
    }
}
