package com.nowcoder.community.notice.service;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NoticeProjectionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(NoticeProjectionApplicationService.class);

    private final NoticeProjectionService noticeProjectionService;

    public NoticeProjectionApplicationService(NoticeProjectionService noticeProjectionService) {
        this.noticeProjectionService = noticeProjectionService;
    }

    public void projectContentEvent(ContentContractEvent event) {
        if (event == null) {
            return;
        }
        try {
            noticeProjectionService.project(noticeProjectionService.commandForContentEvent(event));
        } catch (RuntimeException e) {
            log.warn("[notice] projection failed after commit (eventId={}, type={}): {}", event.eventId(), event.type(), e.toString());
        }
    }

    public void projectSocialEvent(SocialContractEvent event) {
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
