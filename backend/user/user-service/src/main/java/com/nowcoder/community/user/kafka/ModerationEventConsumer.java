package com.nowcoder.community.user.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.contracts.event.EventEnvelopeParser;
import com.nowcoder.community.contracts.event.EventTopics;
import com.nowcoder.community.contracts.event.UnknownEventAction;
import com.nowcoder.community.platform.kafka.KafkaTraceSupport;
import com.nowcoder.community.content.api.event.ContentEventTypes;
import com.nowcoder.community.content.api.event.payload.ModerationCommandPayload;
import com.nowcoder.community.user.dao.ConsumedEventMapper;
import com.nowcoder.community.user.service.InternalUserService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处罚命令消费者（最终一致）：
 * - 输入：content-service 发出的 ModerationCommandRequested 事件（Kafka + 可选 Outbox）
 * - 执行：写入 user.mute_until/ban_until
 * - 输出：发布 ModerationStatusChanged 事件，供下游投影更新
 */
@Component
public class ModerationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ModerationEventConsumer.class);
    private static final Set<String> LOGGED_UNKNOWN_TYPES = ConcurrentHashMap.newKeySet();

    private final ObjectMapper objectMapper;
    private final ConsumedEventMapper consumedEventMapper;
    private final InternalUserService internalUserService;
    private final UnknownEventAction unknownTypeAction;
    private final UnknownEventAction unsupportedVersionAction;

    public ModerationEventConsumer(
            ObjectMapper objectMapper,
            ConsumedEventMapper consumedEventMapper,
            InternalUserService internalUserService,
            @Value("${community.kafka.consumer.unknown-type-action:SKIP}") String unknownTypeAction,
            @Value("${community.kafka.consumer.unsupported-version-action:DLQ}") String unsupportedVersionAction
    ) {
        this.objectMapper = objectMapper;
        this.consumedEventMapper = consumedEventMapper;
        this.internalUserService = internalUserService;
        this.unknownTypeAction = UnknownEventAction.parseOrDefault(unknownTypeAction, UnknownEventAction.SKIP);
        this.unsupportedVersionAction = UnknownEventAction.parseOrDefault(unsupportedVersionAction, UnknownEventAction.DLQ);
    }

    @KafkaListener(topics = EventTopics.MODERATION_EVENTS_V1, groupId = "user-service")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        KafkaTraceSupport.runWithTraceId(objectMapper, record.value(), () -> handleRecord(record));
        ack.acknowledge();
    }

    @Transactional
    void handleRecord(ConsumerRecord<String, String> record) {
        EventEnvelopeParser.ParsedEnvelope env = EventEnvelopeParser.parse(objectMapper, record.value());
        String eventId = env.getEventId();
        String type = env.getType();
        int version = env.getVersion();

        if (version != 1) {
            if (unsupportedVersionAction == UnknownEventAction.SKIP) {
                log.warn("skip unsupported envelope version: {}, eventId={}, type={}", version, eventId, type);
                return;
            }
            throw new IllegalArgumentException("unsupported envelope version: " + version);
        }

        if (!ContentEventTypes.MODERATION_COMMAND_REQUESTED.equals(type)) {
            if (unknownTypeAction == UnknownEventAction.SKIP) {
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

        ModerationCommandPayload cmd = objectMapper.convertValue(env.getPayload(), ModerationCommandPayload.class);
        if (cmd == null || cmd.getUserId() == null || cmd.getUserId() <= 0) {
            throw new IllegalArgumentException("payload.userId 缺失");
        }

        String action = cmd.getAction() == null ? "" : cmd.getAction().trim();
        int durationSeconds = cmd.getDurationSeconds() == null ? 0 : cmd.getDurationSeconds();

        // applyModeration 内部会发布 ModerationStatusChanged 事件（统一出口）
        internalUserService.applyModeration(cmd.getUserId(), action, durationSeconds);
    }

    private boolean markConsumedIfFirstTime(String eventId) {
        try {
            consumedEventMapper.insert(eventId);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

}
