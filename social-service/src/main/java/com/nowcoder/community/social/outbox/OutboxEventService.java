package com.nowcoder.community.social.outbox;

// Outbox 事件服务：负责任务入库、批量认领与状态更新。
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class OutboxEventService {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventService.class);

    private final OutboxEventMapper outboxEventMapper;
    private final SocialOutboxProperties properties;

    // 运行期探测：若数据库不支持 SKIP LOCKED，自动降级一次，避免持续报错/影响主链路。
    private final AtomicBoolean skipLockedUsable = new AtomicBoolean(true);

    public OutboxEventService(OutboxEventMapper outboxEventMapper, SocialOutboxProperties properties) {
        this.outboxEventMapper = outboxEventMapper;
        this.properties = properties;
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
        List<Long> ids = selectCandidateIds(now, size);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        outboxEventMapper.markSending(ids, now);
        return outboxEventMapper.selectByIds(ids);
    }

    private List<Long> selectCandidateIds(Date now, int size) {
        boolean trySkipLocked = properties != null && properties.isClaimSkipLockedEnabled() && skipLockedUsable.get();
        if (trySkipLocked) {
            try {
                return outboxEventMapper.selectCandidateIdsSkipLocked(now, size);
            } catch (Exception ex) {
                if (skipLockedUsable.compareAndSet(true, false)) {
                    log.warn("[outbox] claim SKIP LOCKED not usable, fallback to FOR UPDATE: {}", ex.toString());
                }
            }
        }
        return outboxEventMapper.selectCandidateIds(now, size);
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

    /**
     * 回收卡在 SENDING 的事件（lease 超时）。
     *
     * <p>场景：relay 进程在 markSending 后崩溃，事件会长期停留在 SENDING，导致“隐性丢事件”。</p>
     *
     * @return 实际回收数量
     */
    public int recoverStuckSending(long sendingStaleMs, int limit) {
        long staleMs = Math.max(1000L, sendingStaleMs);
        int size = Math.max(1, Math.min(1000, limit));
        Date now = new Date();
        Date before = new Date(now.getTime() - staleMs);
        List<Long> ids = outboxEventMapper.selectStuckSendingIds(before, size);
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return outboxEventMapper.markRetrySendingByIds(ids, now, "stuck SENDING recovered");
    }

    /**
     * 清理已投递成功（SENT）的历史事件（按保留期）。
     *
     * <p>默认不启用：避免误删与出乎预期的数据丢失风险；需运维评估后开启。</p>
     *
     * @return 实际删除数量
     */
    public int cleanupSent(int retentionDays, int limit) {
        int days = Math.max(1, retentionDays);
        int size = Math.max(1, Math.min(5000, limit));
        long beforeMs = System.currentTimeMillis() - days * 24L * 3600 * 1000;
        Date before = new Date(beforeMs);
        return outboxEventMapper.deleteSentBefore(before, size);
    }
}
