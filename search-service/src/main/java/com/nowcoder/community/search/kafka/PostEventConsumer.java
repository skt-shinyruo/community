package com.nowcoder.community.search.kafka;

// 帖子事件消费者：先执行业务索引副作用（幂等 upsert/delete），成功后再写入幂等表，避免丢更新窗口。
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.common.event.EventTypes;
import com.nowcoder.community.common.event.payload.PostPayload;
import com.nowcoder.community.common.kafka.KafkaTraceSupport;
import com.nowcoder.community.search.repo.PostSearchRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class PostEventConsumer {

    private final ObjectMapper objectMapper;
    private final PostSearchRepository postSearchRepository;
    private final SearchConsumedEventStore consumedEventStore;

    public PostEventConsumer(ObjectMapper objectMapper, PostSearchRepository postSearchRepository, SearchConsumedEventStore consumedEventStore) {
        this.objectMapper = objectMapper;
        this.postSearchRepository = postSearchRepository;
        this.consumedEventStore = consumedEventStore;
    }

    @KafkaListener(topics = EventTopics.POST_EVENTS_V1, groupId = "search-service")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        KafkaTraceSupport.runWithTraceId(objectMapper, record.value(), () -> handleRecord(record));
        ack.acknowledge();
    }

    void handleRecord(ConsumerRecord<String, String> record) throws Exception {
        JsonNode root = objectMapper.readTree(record.value());
        String eventId = text(root, "eventId");
        String type = text(root, "type");
        int version = root.path("version").asInt(0);

        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId 缺失");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type 缺失");
        }

        // 仅支持 v1 envelope；其他版本直接进入错误处理（DLQ），避免 silent drop。
        if (version != 1) {
            throw new IllegalArgumentException("unsupported envelope version: " + version);
        }

        // 幂等：已消费过的 eventId 直接跳过（避免重复索引副作用）
        if (consumedEventStore.isConsumed(eventId)) {
            return;
        }

        PostPayload payload = objectMapper.treeToValue(root.get("payload"), PostPayload.class);
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

        // 未知 type：进入错误处理（DLQ）
        throw new IllegalArgumentException("unsupported event type: " + type);
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
