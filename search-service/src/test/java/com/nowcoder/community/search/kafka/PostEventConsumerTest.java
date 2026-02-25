package com.nowcoder.community.search.kafka;

// search-service 幂等消费测试：重复 eventId 只应索引一次。
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.contracts.event.EventTopics;
import com.nowcoder.community.content.api.event.ContentEventTypes;
import com.nowcoder.community.search.repo.PostSearchRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PostEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final PostSearchRepository postSearchRepository = mock(PostSearchRepository.class);
    private final InMemorySearchConsumedEventStore consumedEventStore = new InMemorySearchConsumedEventStore();
    private final PostEventConsumer consumer = new PostEventConsumer(
            objectMapper,
            postSearchRepository,
            consumedEventStore,
            "SKIP",
            "DLQ"
    );

    @Test
    void shouldSkipDuplicateEventId() throws Exception {
        String json = objectMapper.createObjectNode()
                .put("eventId", "event-1")
                .put("type", ContentEventTypes.POST_PUBLISHED)
                .put("version", 1)
                .set("payload", objectMapper.createObjectNode().put("postId", 100))
                .toString();

        ConsumerRecord<String, String> record = new ConsumerRecord<>(EventTopics.POST_EVENTS_V1, 0, 0L, "k", json);

        consumer.handleRecord(record);
        consumer.handleRecord(record);

        assertThat(consumedEventStore.size()).isEqualTo(1);
        verify(postSearchRepository, times(1)).upsert(any());
    }

    @Test
    void shouldNotMarkConsumedWhenEsWriteFailsThenAllowRetry() throws Exception {
        String json = objectMapper.createObjectNode()
                .put("eventId", "event-fail-1")
                .put("type", ContentEventTypes.POST_PUBLISHED)
                .put("version", 1)
                .set("payload", objectMapper.createObjectNode().put("postId", 101))
                .toString();

        ConsumerRecord<String, String> record = new ConsumerRecord<>(EventTopics.POST_EVENTS_V1, 0, 0L, "k", json);

        doThrow(new RuntimeException("es down"))
                .when(postSearchRepository)
                .upsert(any());

        assertThatThrownBy(() -> consumer.handleRecord(record))
                .isInstanceOf(RuntimeException.class);

        assertThat(consumedEventStore.size()).isEqualTo(0);

        // 第二次：模拟 ES 恢复后可正常写入并标记已消费
        org.mockito.Mockito.doNothing()
                .when(postSearchRepository)
                .upsert(any());
        consumer.handleRecord(record);

        assertThat(consumedEventStore.size()).isEqualTo(1);

        verify(postSearchRepository, times(2)).upsert(any());
    }

    private static class InMemorySearchConsumedEventStore extends SearchConsumedEventStore {

        private final Set<String> consumed = ConcurrentHashMap.newKeySet();

        private InMemorySearchConsumedEventStore() {
            super(mock(org.springframework.jdbc.core.JdbcTemplate.class));
        }

        @Override
        public boolean isConsumed(String eventId) {
            return eventId != null && !eventId.isBlank() && consumed.contains(eventId);
        }

        @Override
        public boolean markConsumedIfFirst(String eventId) {
            return eventId != null && !eventId.isBlank() && consumed.add(eventId);
        }

        private int size() {
            return consumed.size();
        }
    }
}
