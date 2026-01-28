package com.nowcoder.community.content.outbox;

// Outbox 事件服务：负责任务入库、批量认领与状态更新。
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
public class OutboxEventService {

    private final OutboxEventMapper outboxEventMapper;

    public OutboxEventService(OutboxEventMapper outboxEventMapper) {
        this.outboxEventMapper = outboxEventMapper;
    }

    public void enqueue(String eventId, String topic, String eventKey, String payload) {
        if (!StringUtils.hasText(eventId) || !StringUtils.hasText(topic) || !StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("outbox 事件字段缺失");
        }
        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setTopic(topic);
        event.setEventKey(eventKey == null ? "" : eventKey);
        event.setPayload(payload);
        event.setStatus("NEW");
        event.setRetryCount(0);
        outboxEventMapper.insert(event);
    }

    @Transactional
    public List<OutboxEvent> claimBatch(int limit) {
        int size = Math.max(1, Math.min(500, limit));
        Date now = new Date();
        List<Long> ids = outboxEventMapper.selectCandidateIds(now, size);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        outboxEventMapper.markSending(ids, now);
        return outboxEventMapper.selectByIds(ids);
    }

    public void markSent(long id) {
        outboxEventMapper.markSent(id, new Date());
    }

    public void markRetry(long id, int retryCount, Date nextRetryAt, String lastError) {
        outboxEventMapper.markRetry(id, retryCount, nextRetryAt, lastError, new Date());
    }

    public void markFailed(long id, String lastError) {
        outboxEventMapper.markFailed(id, lastError, new Date());
    }

    public int countByStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return 0;
        }
        return outboxEventMapper.countByStatus(status);
    }

    public int replayFailed(int limit) {
        int size = Math.max(1, Math.min(1000, limit));
        List<Long> ids = outboxEventMapper.selectFailedIds(size);
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return outboxEventMapper.markRetryByIds(ids, new Date());
    }
}
