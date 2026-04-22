package com.nowcoder.community.notice.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.notice.service.NoticeProjectionService;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * BEFORE_COMMIT enqueuer for notice projection.
 */
@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class NoticeOutboxEnqueuer {

    private static final String OUTBOX_EVENT_SUFFIX = ":notice";

    private final ObjectMapper objectMapper;
    private final JdbcOutboxEventStore store;
    private final NoticeProjectionService noticeProjectionService;

    @Autowired
    public NoticeOutboxEnqueuer(ObjectMapper objectMapper, JdbcOutboxEventStore store, NoticeProjectionService noticeProjectionService) {
        this.objectMapper = objectMapper;
        this.store = store;
        this.noticeProjectionService = noticeProjectionService;
    }

    NoticeOutboxEnqueuer(ObjectMapper objectMapper, JdbcOutboxEventStore store) {
        this(objectMapper, store, new NoticeProjectionService(objectMapper, null));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentContractEvent event) {
        enqueue(noticeProjectionService.commandForContentEvent(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onSocialEvent(SocialContractEvent event) {
        enqueue(noticeProjectionService.commandForSocialEvent(event));
    }

    private void enqueue(NoticeProjectionService.NoticeProjectionCommand command) {
        if (command == null || command.toUserId() == null) {
            return;
        }
        if (command.sourceEventId() == null || command.sourceEventId().isBlank()) {
            return;
        }
        if (command.sourceEventType() == null || command.sourceEventType().isBlank()) {
            return;
        }
        if (command.topic() == null || command.topic().isBlank()) {
            return;
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("notice outbox payload 序列化失败: " + command.sourceEventType(), e);
        }

        store.enqueue(command.sourceEventId() + OUTBOX_EVENT_SUFFIX, NoticeOutboxHandler.TOPIC, String.valueOf(command.toUserId()), payloadJson);
    }
}
