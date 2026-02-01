package com.nowcoder.community.search.kafka;

// 帖子事件消费者：先执行业务索引副作用（幂等 upsert/delete），成功后再写入幂等表，避免丢更新窗口。
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventEnvelopeParser;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.common.event.EventTypes;
import com.nowcoder.community.common.event.UnknownEventAction;
import com.nowcoder.community.common.event.payload.PostPayload;
import com.nowcoder.community.common.kafka.KafkaTraceSupport;
import com.nowcoder.community.search.repo.PostSearchRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PostEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PostEventConsumer.class);
    private static final Set<String> LOGGED_UNKNOWN_TYPES = ConcurrentHashMap.newKeySet();

    private final ObjectMapper objectMapper;
    private final PostSearchRepository postSearchRepository;
    private final SearchConsumedEventStore consumedEventStore;
    private final UnknownEventAction unknownTypeAction;
    private final UnknownEventAction unsupportedVersionAction;

    public PostEventConsumer(
            ObjectMapper objectMapper,
            PostSearchRepository postSearchRepository,
            SearchConsumedEventStore consumedEventStore,
            @Value("${community.kafka.consumer.unknown-type-action:SKIP}") String unknownTypeAction,
            @Value("${community.kafka.consumer.unsupported-version-action:DLQ}") String unsupportedVersionAction
    ) {
        this.objectMapper = objectMapper;
        this.postSearchRepository = postSearchRepository;
        this.consumedEventStore = consumedEventStore;
        this.unknownTypeAction = UnknownEventAction.parseOrDefault(unknownTypeAction, UnknownEventAction.SKIP);
        this.unsupportedVersionAction = UnknownEventAction.parseOrDefault(unsupportedVersionAction, UnknownEventAction.DLQ);
    }

    @KafkaListener(topics = EventTopics.POST_EVENTS_V1, groupId = "search-service")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        KafkaTraceSupport.runWithTraceId(objectMapper, record.value(), () -> handleRecord(record));
        ack.acknowledge();
    }

    void handleRecord(ConsumerRecord<String, String> record) throws Exception {
        EventEnvelopeParser.ParsedEnvelope env = EventEnvelopeParser.parse(objectMapper, record.value());
        String eventId = env.getEventId();
        String type = env.getType();
        int version = env.getVersion();

        // 仅支持 v1 envelope；其他版本直接进入错误处理（DLQ），避免 silent drop。
        if (version != 1) {
            if (unsupportedVersionAction == UnknownEventAction.SKIP) {
                log.warn("skip unsupported envelope version: {}, eventId={}, type={}", version, eventId, type);
                return;
            }
            throw new IllegalArgumentException("unsupported envelope version: " + version);
        }

        // 幂等：已消费过的 eventId 直接跳过（避免重复索引副作用）
        if (consumedEventStore.isConsumed(eventId)) {
            return;
        }

        PostPayload payload = objectMapper.treeToValue(env.getPayload(), PostPayload.class);
        if (payload == null || payload.getPostId() <= 0) {
            throw new IllegalArgumentException("postId 缺失");
        }

        if (EventTypes.POST_DELETED.equals(type)) {
            postSearchRepository.delete(payload.getPostId());
            // 索引副作用成功后再标记已消费（幂等点位后移）
            consumedEventStore.markConsumedIfFirst(eventId);
            return;
        }

        if (EventTypes.POST_PUBLISHED.equals(type) || EventTypes.POST_UPDATED.equals(type)) {
            postSearchRepository.upsert(payload);
            consumedEventStore.markConsumedIfFirst(eventId);
            return;
        }

        // 未知 type：默认 SKIP（按 type 去重告警），也可配置为 DLQ 触发更严格的告警/排障。
        if (unknownTypeAction == UnknownEventAction.SKIP) {
            if (LOGGED_UNKNOWN_TYPES.add(type)) {
                log.warn("skip unsupported event type: {}, example eventId={}", type, eventId);
            }
            return;
        }
        throw new IllegalArgumentException("unsupported event type: " + type);
    }
}
