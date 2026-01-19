package com.nowcoder.community.message.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventTypes;
import com.nowcoder.community.common.event.payload.CommentPayload;
import com.nowcoder.community.common.event.payload.FollowPayload;
import com.nowcoder.community.common.event.payload.LikePayload;
import com.nowcoder.community.message.dao.ConsumedEventMapper;
import com.nowcoder.community.message.service.NoticeService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 站内信/通知的 Kafka 消费处理器：
 * - 幂等：以 consumed_event(event_id) 唯一约束为准（insert-first）
 * - 事务：幂等记录与业务写入（notice）同事务，避免“写幂等后失败导致永久丢通知”
 */
@Service
public class NoticeEventProcessor {

    private final ObjectMapper objectMapper;
    private final ConsumedEventMapper consumedEventMapper;
    private final NoticeService noticeService;

    public NoticeEventProcessor(ObjectMapper objectMapper, ConsumedEventMapper consumedEventMapper, NoticeService noticeService) {
        this.objectMapper = objectMapper;
        this.consumedEventMapper = consumedEventMapper;
        this.noticeService = noticeService;
    }

    /**
     * 仅在处理成功后由上层 listener ack。
     * 发生异常将触发 Spring Kafka 的重试与 DLQ（由 DefaultErrorHandler 统一接管）。
     */
    @Transactional
    public void handleRecord(ConsumerRecord<String, String> record) throws Exception {
        JsonNode root = objectMapper.readTree(record.value());
        String eventId = text(root, "eventId");
        String type = text(root, "type");

        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId 缺失");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type 缺失");
        }

        // 不支持的事件类型：按约定标记已消费（避免无限重试占用消费能力），不产生业务副作用。
        if (!EventTypes.COMMENT_CREATED.equals(type)
                && !EventTypes.LIKE_CREATED.equals(type)
                && !EventTypes.FOLLOW_CREATED.equals(type)) {
            markConsumedIfFirstTime(eventId);
            return;
        }

        // 幂等：先插入 eventId 作为“幂等锁”，插入失败视为已处理过，直接返回。
        if (!markConsumedIfFirstTime(eventId)) {
            return;
        }

        int toUserId = 0;
        String topic = null;
        Object payload = null;

        if (EventTypes.COMMENT_CREATED.equals(type)) {
            CommentPayload p = objectMapper.treeToValue(root.get("payload"), CommentPayload.class);
            payload = p;
            topic = "comment";
            toUserId = p.getTargetUserId() == null ? 0 : p.getTargetUserId();
        } else if (EventTypes.LIKE_CREATED.equals(type)) {
            LikePayload p = objectMapper.treeToValue(root.get("payload"), LikePayload.class);
            payload = p;
            topic = "like";
            toUserId = p.getEntityUserId() == null ? 0 : p.getEntityUserId();
        } else if (EventTypes.FOLLOW_CREATED.equals(type)) {
            FollowPayload p = objectMapper.treeToValue(root.get("payload"), FollowPayload.class);
            payload = p;
            topic = "follow";
            toUserId = p.getEntityUserId() == null ? 0 : p.getEntityUserId();
        }

        if (toUserId <= 0) {
            // 目标用户缺失：认为该事件“无需通知”，保持已消费即可
            return;
        }

        String contentJson = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "type", type,
                "payload", payload
        ));
        noticeService.createNotice(toUserId, topic, contentJson);
    }

    /**
     * @return true 表示首次标记成功（应继续执行副作用），false 表示已消费过（应直接返回）
     */
    private boolean markConsumedIfFirstTime(String eventId) {
        try {
            consumedEventMapper.insert(eventId);
            return true;
        } catch (DataIntegrityViolationException e) {
            // 唯一约束冲突（重复消费） -> 幂等返回
            return false;
        }
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String s = node.asText();
        return s == null || s.isBlank() ? null : s;
    }
}

