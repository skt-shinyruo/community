package com.nowcoder.community.notice.event;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.notice.service.NoticeProjectionApplicationService;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NoticeProjectionListener {

    private final NoticeProjectionApplicationService noticeProjectionApplicationService;

    public NoticeProjectionListener(NoticeProjectionApplicationService noticeProjectionApplicationService) {
        this.noticeProjectionApplicationService = noticeProjectionApplicationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentContractEvent event) {
        noticeProjectionApplicationService.projectContentEvent(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onSocialEvent(SocialContractEvent event) {
        noticeProjectionApplicationService.projectSocialEvent(event);
    }
}
