package com.nowcoder.community.message.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.event.ContentEventTypes;
import com.nowcoder.community.content.event.payload.CommentPayload;
import com.nowcoder.community.content.event.ContentLocalEvent;
import com.nowcoder.community.message.service.NoticeService;
import com.nowcoder.community.social.event.SocialEventTypes;
import com.nowcoder.community.social.event.payload.LikePayload;
import com.nowcoder.community.social.event.SocialLocalEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NoticeProjectionListenerTest {

    @Test
    void commentCreatedShouldCreateCommentNotice() {
        NoticeService noticeService = mock(NoticeService.class);
        NoticeProjectionListener listener = new NoticeProjectionListener(new ObjectMapper().findAndRegisterModules(), noticeService);

        CommentPayload payload = new CommentPayload();
        payload.setTargetUserId(9);
        payload.setPostId(100);

        listener.onContentEvent(new ContentLocalEvent("evt-comment-1", ContentEventTypes.COMMENT_CREATED, payload));

        verify(noticeService).createNotice(eq(9), eq("comment"), contains("evt-comment-1"));
    }

    @Test
    void likeCreatedShouldCreateLikeNotice() {
        NoticeService noticeService = mock(NoticeService.class);
        NoticeProjectionListener listener = new NoticeProjectionListener(new ObjectMapper().findAndRegisterModules(), noticeService);

        LikePayload payload = new LikePayload();
        payload.setEntityUserId(8);
        payload.setEntityId(200);

        listener.onSocialEvent(new SocialLocalEvent("evt-like-1", SocialEventTypes.LIKE_CREATED, payload));

        verify(noticeService).createNotice(eq(8), eq("like"), contains("evt-like-1"));
    }
}
