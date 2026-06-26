package com.nowcoder.community.growth.infrastructure.event;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class LikeTaskProgressKafkaOutboxEnqueuer {

    private static final String OUTBOX_EVENT_SUFFIX = ":growth_task";

    private final JsonCodec jsonCodec;
    private final JdbcOutboxEventStore store;
    private final String topic;

    public LikeTaskProgressKafkaOutboxEnqueuer(
            JsonCodec jsonCodec,
            JdbcOutboxEventStore store,
            @Value("${growth.task.outbox.like-topic:projection.growth.task.like}") String topic
    ) {
        this.jsonCodec = jsonCodec;
        this.store = store;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onSocialEvent(SocialContractEvent event) {
        if (event == null || !SocialEventTypes.LIKE_CREATED.equals(event.type())) {
            return;
        }
        if (!(event.payload() instanceof LikePayload payload)
                || payload.getActorUserId() == null
                || payload.getEntityId() == null
                || payload.getEntityUserId() == null
                || payload.getCreateTime() == null
                || payload.getActorUserId().equals(payload.getEntityUserId())) {
            return;
        }

        String payloadJson;
        try {
            payloadJson = jsonCodec.toJson(payload);
        } catch (JsonCodecException e) {
            throw new IllegalStateException("growth task like outbox payload 序列化失败", e);
        }

        store.enqueue(
                sourceEventId(payload) + OUTBOX_EVENT_SUFFIX,
                topic,
                payload.getEntityUserId().toString(),
                payloadJson
        );
    }

    private String sourceEventId(LikePayload payload) {
        if (StringUtils.hasText(payload.getRelationKey())) {
            return payload.getRelationKey().trim();
        }
        return "like-created:" + payload.getActorUserId() + ":" + payload.getEntityType() + ":" + payload.getEntityId();
    }
}
