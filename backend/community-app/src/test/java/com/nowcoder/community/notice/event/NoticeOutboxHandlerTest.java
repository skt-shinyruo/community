package com.nowcoder.community.notice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.notice.service.NoticeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NoticeOutboxHandlerTest {

    @Test
    void handlerShouldCreateNoticeWithContentJson() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        NoticeService noticeService = mock(NoticeService.class);
        UUID toUserId = uuid(9);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        NoticeOutboxHandler handler = new NoticeOutboxHandler(objectMapper, noticeService);

        String payloadJson = objectMapper.writeValueAsString(Map.of(
                "toUserId", toUserId,
                "topic", "comment",
                "sourceEventId", "src-n1",
                "sourceEventType", "CommentCreated",
                "payload", Map.of("postId", postId, "commentId", commentId)
        ));

        OutboxEvent event = new OutboxEvent(
                UUID.fromString("01965429-b34a-7000-8000-000000000031"),
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
        verify(noticeService).createNotice(eq(toUserId), eq("comment"), contentCaptor.capture());

        String contentJson = contentCaptor.getValue();
        assertThat(contentJson).contains("\"eventId\":\"src-n1\"");
        assertThat(contentJson).contains("\"type\":\"CommentCreated\"");
        assertThat(contentJson).contains("\"commentId\":\"" + commentId + "\"");
    }
}
