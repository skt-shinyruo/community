package com.nowcoder.community.user.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.user.service.PointsProjectionService;
import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * BEFORE_COMMIT enqueuer for points projection.
 *
 * <p>Writes an outbox row in the same DB transaction so that points projection is retryable and does not affect HTTP responses.</p>
 */
@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class PointsOutboxEnqueuer {

    private static final String OUTBOX_EVENT_SUFFIX = ":points";

    private final ObjectMapper objectMapper;
    private final JdbcOutboxEventStore store;
    private final PointsProjectionService pointsProjectionService;

    @Autowired
    public PointsOutboxEnqueuer(ObjectMapper objectMapper, JdbcOutboxEventStore store, PointsProjectionService pointsProjectionService) {
        this.objectMapper = objectMapper;
        this.store = store;
        this.pointsProjectionService = pointsProjectionService;
    }

    PointsOutboxEnqueuer(ObjectMapper objectMapper, JdbcOutboxEventStore store) {
        this(objectMapper, store, new PointsProjectionService((WalletRewardActionApi) null));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentContractEvent event) {
        enqueue(pointsProjectionService.commandForContentEvent(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onSocialEvent(SocialContractEvent event) {
        enqueue(pointsProjectionService.commandForSocialEvent(event));
    }

    private void enqueue(PointsProjectionService.PointsProjectionCommand command) {
        if (command == null || command.userId() == null || command.delta() == 0) {
            return;
        }
        if (command.sourceEventId() == null || command.sourceEventId().isBlank()) {
            return;
        }
        if (command.sourceEventType() == null || command.sourceEventType().isBlank()) {
            return;
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("points outbox payload 序列化失败", e);
        }

        store.enqueue(command.sourceEventId() + OUTBOX_EVENT_SUFFIX, PointsOutboxHandler.TOPIC, String.valueOf(command.userId()), payloadJson);
    }
}
