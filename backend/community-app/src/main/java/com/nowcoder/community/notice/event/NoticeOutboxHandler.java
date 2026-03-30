package com.nowcoder.community.notice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.infra.outbox.OutboxEvent;
import com.nowcoder.community.infra.outbox.OutboxHandler;
import com.nowcoder.community.notice.service.NoticeProjectionService;
import com.nowcoder.community.notice.service.NoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Outbox handler for notice projection.
 */
@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class NoticeOutboxHandler implements OutboxHandler {

    public static final String TOPIC = "projection.notice";

    private final ObjectMapper objectMapper;
    private final NoticeProjectionService noticeProjectionService;

    @Autowired
    public NoticeOutboxHandler(ObjectMapper objectMapper, NoticeProjectionService noticeProjectionService) {
        this.objectMapper = objectMapper;
        this.noticeProjectionService = noticeProjectionService;
    }

    NoticeOutboxHandler(ObjectMapper objectMapper, NoticeService noticeService) {
        this(objectMapper, new NoticeProjectionService(objectMapper, noticeService));
    }

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public void handle(OutboxEvent event) {
        if (event == null || !StringUtils.hasText(event.payload())) {
            return;
        }
        NoticeProjectionService.NoticeProjectionCommand payload;
        try {
            payload = objectMapper.readValue(event.payload(), NoticeProjectionService.NoticeProjectionCommand.class);
        } catch (Exception e) {
            throw new IllegalStateException("notice outbox payload 反序列化失败", e);
        }
        noticeProjectionService.project(payload);
    }
}
