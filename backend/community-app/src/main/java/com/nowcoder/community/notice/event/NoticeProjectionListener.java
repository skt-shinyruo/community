package com.nowcoder.community.notice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.notice.service.NoticeProjectionService;
import com.nowcoder.community.notice.service.NoticeService;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoticeProjectionListener {

    private static final Logger log = LoggerFactory.getLogger(NoticeProjectionListener.class);

    private final NoticeProjectionService noticeProjectionService;

    @Autowired
    public NoticeProjectionListener(NoticeProjectionService noticeProjectionService) {
        this.noticeProjectionService = noticeProjectionService;
    }

    NoticeProjectionListener(ObjectMapper objectMapper, NoticeService noticeService) {
        this(new NoticeProjectionService(objectMapper, noticeService));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentContractEvent event) {
        if (event == null) {
            return;
        }
        try {
            noticeProjectionService.project(noticeProjectionService.commandForContentEvent(event));
        } catch (RuntimeException e) {
            log.warn("[notice] projection failed after commit (eventId={}, type={}): {}", event.eventId(), event.type(), e.toString());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onSocialEvent(SocialContractEvent event) {
        if (event == null) {
            return;
        }
        try {
            noticeProjectionService.project(noticeProjectionService.commandForSocialEvent(event));
        } catch (RuntimeException e) {
            log.warn("[notice] projection failed after commit (eventId={}, type={}): {}", event.eventId(), event.type(), e.toString());
        }
    }
}
