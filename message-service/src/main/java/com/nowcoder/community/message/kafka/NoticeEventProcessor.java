package com.nowcoder.community.message.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.nowcoder.community.common.event.EventEnvelopeParser;
import com.nowcoder.community.common.event.EventTypes;
import com.nowcoder.community.common.event.UnknownEventAction;
import com.nowcoder.community.common.event.payload.CommentPayload;
import com.nowcoder.community.common.event.payload.FollowPayload;
import com.nowcoder.community.common.event.payload.LikePayload;
import com.nowcoder.community.common.event.payload.ModerationPayload;
import com.nowcoder.community.message.dao.ConsumedEventMapper;
import com.nowcoder.community.message.service.NoticeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 站内信/通知的 Kafka 消费处理器：
 * - 幂等：以 consumed_event(event_id) 唯一约束为准（insert-first）
 * - 事务：幂等记录与业务写入（notice）同事务，避免“写幂等后失败导致永久丢通知”
 */
@Service
public class NoticeEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(NoticeEventProcessor.class);
    private static final Set<String> LOGGED_UNKNOWN_TYPES = ConcurrentHashMap.newKeySet();

    private final ObjectMapper objectMapper;
    private final ConsumedEventMapper consumedEventMapper;
    private final NoticeService noticeService;
    private final UnknownEventAction unknownTypeAction;
    private final UnknownEventAction unsupportedVersionAction;

    public NoticeEventProcessor(
            ObjectMapper objectMapper,
            ConsumedEventMapper consumedEventMapper,
            NoticeService noticeService,
            @Value("${community.kafka.consumer.unknown-type-action:SKIP}") String unknownTypeAction,
            @Value("${community.kafka.consumer.unsupported-version-action:DLQ}") String unsupportedVersionAction
    ) {
        this.objectMapper = objectMapper;
        this.consumedEventMapper = consumedEventMapper;
        this.noticeService = noticeService;
        this.unknownTypeAction = UnknownEventAction.parseOrDefault(unknownTypeAction, UnknownEventAction.SKIP);
        this.unsupportedVersionAction = UnknownEventAction.parseOrDefault(unsupportedVersionAction, UnknownEventAction.DLQ);
    }

    /**
     * 仅在处理成功后由上层 listener ack。
     * 发生异常将触发 Spring Kafka 的重试与 DLQ（由 DefaultErrorHandler 统一接管）。
     */
    @Transactional
    public void handleRecord(ConsumerRecord<String, String> record) {
        EventEnvelopeParser.ParsedEnvelope env = EventEnvelopeParser.parse(objectMapper, record.value());
        String eventId = env.getEventId();
        String type = env.getType();
        int version = env.getVersion();

        // 仅支持 v1：版本不匹配进入错误处理（DLQ），避免 silent drop。
        if (version != 1) {
            if (unsupportedVersionAction == UnknownEventAction.SKIP) {
                log.warn("skip unsupported envelope version: {}, eventId={}, type={}", version, eventId, type);
                return;
            }
            throw new IllegalArgumentException("unsupported envelope version: " + version);
        }

        int toUserId = 0;
        String topic = null;
        Object payload = null;

        if (EventTypes.COMMENT_CREATED.equals(type)) {
            CommentPayload p = objectMapper.convertValue(env.getPayload(), CommentPayload.class);
            payload = p;
            topic = "comment";
            toUserId = p.getTargetUserId() == null ? 0 : p.getTargetUserId();
        } else if (EventTypes.LIKE_CREATED.equals(type)) {
            LikePayload p = objectMapper.convertValue(env.getPayload(), LikePayload.class);
            payload = p;
            topic = "like";
            toUserId = p.getEntityUserId() == null ? 0 : p.getEntityUserId();
        } else if (EventTypes.FOLLOW_CREATED.equals(type)) {
            FollowPayload p = objectMapper.convertValue(env.getPayload(), FollowPayload.class);
            payload = p;
            topic = "follow";
            toUserId = p.getEntityUserId() == null ? 0 : p.getEntityUserId();
        } else if (EventTypes.MODERATION_ACTION_APPLIED.equals(type)) {
            ModerationPayload p = objectMapper.convertValue(env.getPayload(), ModerationPayload.class);
            payload = p;
            topic = "moderation";
            toUserId = p.getToUserId() == null ? 0 : p.getToUserId();
        } else {
            if (unknownTypeAction == UnknownEventAction.SKIP) {
                // 该 consumer 订阅多个 domain topic（COMMENT/SOCIAL/MODERATION），未知 type 可能仅代表“本 consumer 不关心”。
                if (LOGGED_UNKNOWN_TYPES.add(type)) {
                    log.warn("skip unsupported event type: {}, example eventId={}", type, eventId);
                }
                return;
            }
            throw new IllegalArgumentException("unsupported event type: " + type);
        }

        // 幂等：先插入 eventId 作为“幂等锁”，插入失败视为已处理过，直接返回。
        if (!markConsumedIfFirstTime(eventId)) {
            return;
        }

        if (toUserId <= 0) {
            // 目标用户缺失：认为该事件“无需通知”，保持已消费即可
            return;
        }

        String contentJson;
        try {
            contentJson = objectMapper.writeValueAsString(Map.of(
                    "eventId", eventId,
                    "type", type,
                    "payload", payload
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("notice payload 序列化失败: " + type, e);
        }
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

}
