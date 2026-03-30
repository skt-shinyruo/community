package com.nowcoder.community.notice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.notice.service.NoticeService;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
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

        listener.onContentEvent(new ContentContractEvent("evt-comment-1", ContentEventTypes.COMMENT_CREATED, payload));

        verify(noticeService).createNotice(eq(9), eq("comment"), contains("evt-comment-1"));
    }

    @Test
    void likeCreatedShouldCreateLikeNotice() {
        NoticeService noticeService = mock(NoticeService.class);
        NoticeProjectionListener listener = new NoticeProjectionListener(new ObjectMapper().findAndRegisterModules(), noticeService);

        LikePayload payload = new LikePayload();
        payload.setEntityUserId(8);
        payload.setEntityId(200);

        listener.onSocialEvent(new SocialContractEvent("evt-like-1", SocialEventTypes.LIKE_CREATED, payload));

        verify(noticeService).createNotice(eq(8), eq("like"), contains("evt-like-1"));
    }
}
