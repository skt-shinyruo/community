package com.nowcoder.community.message.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.common.event.EventTypes;
import com.nowcoder.community.message.dao.ConsumedEventMapper;
import com.nowcoder.community.message.service.NoticeService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class NoticeEventProcessorTxTest {

    @Autowired
    NoticeEventProcessor processor;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ConsumedEventMapper consumedEventMapper;

    @SpyBean
    NoticeService noticeService;

    @Test
    void failAfterIdempotencyInsertShouldRollbackAndAllowRetry() throws Exception {
        String eventId = "tx1";
        int toUserId = 43;

        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "type", EventTypes.LIKE_CREATED,
                "version", 1,
                "occurredAt", Instant.now().toString(),
                "producer", "social-service",
                "payload", Map.of(
                        "actorUserId", 2,
                        "entityType", 1,
                        "entityId", 100,
                        "entityUserId", toUserId,
                        "postId", 100,
                        "createTime", Instant.now().toString()
                )
        ));

        // 第一次写入故意失败：验证事务能回滚，避免“幂等记录已写入 -> 永久跳过”。
        doThrow(new RuntimeException("fail once"))
                .doCallRealMethod()
                .when(noticeService)
                .createNotice(eq(toUserId), eq("like"), anyString());

        try {
            processor.handleRecord(new ConsumerRecord<>(EventTopics.SOCIAL_EVENTS_V1, 0, 0L, "k1", payload));
        } catch (Exception ignored) {
        }

        assertThat(consumedEventMapper.countByEventId(eventId)).isEqualTo(0);

        // 重试后应能成功写入
        processor.handleRecord(new ConsumerRecord<>(EventTopics.SOCIAL_EVENTS_V1, 0, 1L, "k1", payload));

        assertThat(consumedEventMapper.countByEventId(eventId)).isEqualTo(1);
        assertThat(noticeService.listNotices(toUserId, "like", 0, 10).size()).isEqualTo(1);
        assertThat(noticeService.listNotices(toUserId, "like", 0, 10).get(0).getContent()).contains(eventId);
    }
}
