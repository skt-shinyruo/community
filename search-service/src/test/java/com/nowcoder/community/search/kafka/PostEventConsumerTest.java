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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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

    @Test
    void shouldNotMarkConsumedWhenEsWriteFailsThenAllowRetry() throws Exception {
        String json = objectMapper.createObjectNode()
                .put("eventId", "event-fail-1")
                .put("type", EventTypes.POST_PUBLISHED)
                .put("version", 1)
                .set("payload", objectMapper.createObjectNode().put("postId", 101))
                .toString();

        ConsumerRecord<String, String> record = new ConsumerRecord<>(EventTopics.POST_EVENTS_V1, 0, 0L, "k", json);

        doThrow(new RuntimeException("es down"))
                .doNothing()
                .when(postSearchRepository)
                .upsert(any());

        assertThatThrownBy(() -> consumer.handleRecord(record))
                .isInstanceOf(RuntimeException.class);

        Integer c1 = jdbcTemplate.queryForObject("select count(*) from search_consumed_event", Integer.class);
        assertThat(c1 == null ? 0 : c1).isEqualTo(0);

        consumer.handleRecord(record);

        Integer c2 = jdbcTemplate.queryForObject("select count(*) from search_consumed_event", Integer.class);
        assertThat(c2 == null ? 0 : c2).isEqualTo(1);

        verify(postSearchRepository, times(2)).upsert(any());
    }
}
