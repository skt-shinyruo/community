package com.nowcoder.community.notice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.notice.service.NoticeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NoticeOutboxHandlerTest {

    @Test
    void handlerShouldCreateNoticeWithContentJson() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        NoticeService noticeService = mock(NoticeService.class);

        NoticeOutboxHandler handler = new NoticeOutboxHandler(objectMapper, noticeService);

        String payloadJson = objectMapper.writeValueAsString(Map.of(
                "toUserId", 9,
                "topic", "comment",
                "sourceEventId", "src-n1",
                "sourceEventType", "CommentCreated",
                "payload", Map.of("postId", 100, "commentId", 200)
        ));

        OutboxEvent event = new OutboxEvent(
                1L,
                "src-n1:notice",
                NoticeOutboxHandler.TOPIC,
                "9",
                payloadJson,
                "PENDING",
                0,
                null,
                null
        );

        handler.handle(event);

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(noticeService).createNotice(eq(9), eq("comment"), contentCaptor.capture());

        String contentJson = contentCaptor.getValue();
        assertThat(contentJson).contains("\"eventId\":\"src-n1\"");
        assertThat(contentJson).contains("\"type\":\"CommentCreated\"");
        assertThat(contentJson).contains("\"commentId\":200");
    }
}
