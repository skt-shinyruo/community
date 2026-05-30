package com.nowcoder.community.user.infrastructure.event;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class CommentRewardOutboxEnqueuer {

    private static final String OUTBOX_EVENT_SUFFIX = ":user_reward";

    private final JsonCodec jsonCodec;
    private final JdbcOutboxEventStore store;
    private final String topic;

    public CommentRewardOutboxEnqueuer(
            JsonCodec jsonCodec,
            JdbcOutboxEventStore store,
            @Value("${user.reward.outbox.comment-topic:projection.user.reward.comment}") String topic
    ) {
        this.jsonCodec = jsonCodec;
        this.store = store;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentContractEvent event) {
        if (event == null || !ContentEventTypes.COMMENT_CREATED.equals(event.type())) {
            return;
        }
        if (!(event.payload() instanceof CommentPayload payload)
                || payload.getCommentId() == null
                || payload.getUserId() == null) {
            return;
        }

        String payloadJson;
        try {
            payloadJson = jsonCodec.toJson(payload);
        } catch (JsonCodecException e) {
            throw new IllegalStateException("user reward comment outbox payload 序列化失败", e);
        }

        store.enqueue(
                "comment-created:" + payload.getCommentId() + OUTBOX_EVENT_SUFFIX,
                topic,
                payload.getUserId().toString(),
                payloadJson
        );
    }
}
