package com.nowcoder.community.search.kafka;

// search-service 幂等消费测试：重复 eventId 只应索引一次。
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.common.event.EventTypes;
import com.nowcoder.community.search.repo.PostSearchRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class PostEventConsumerTest {

    @Autowired
    PostEventConsumer consumer;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockBean
    PostSearchRepository postSearchRepository;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("delete from search_consumed_event");
    }

    @Test
    void shouldSkipDuplicateEventId() throws Exception {
        String json = objectMapper.createObjectNode()
                .put("eventId", "event-1")
                .put("type", EventTypes.POST_PUBLISHED)
                .put("version", 1)
                .set("payload", objectMapper.createObjectNode().put("postId", 100))
                .toString();

        ConsumerRecord<String, String> record = new ConsumerRecord<>(EventTopics.POST_EVENTS_V1, 0, 0L, "k", json);

        consumer.handleRecord(record);
        consumer.handleRecord(record);

        verify(postSearchRepository, times(1)).upsert(any());
    }
}

