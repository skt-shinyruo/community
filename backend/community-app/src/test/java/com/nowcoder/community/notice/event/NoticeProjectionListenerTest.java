package com.nowcoder.community.notice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.notice.service.NoticeProjectionApplicationService;
import com.nowcoder.community.notice.service.NoticeProjectionService;
import com.nowcoder.community.notice.service.NoticeService;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NoticeProjectionListenerTest {

    @Test
    void commentCreatedShouldCreateCommentNotice() {
        NoticeService noticeService = mock(NoticeService.class);
        NoticeProjectionListener listener = new NoticeProjectionListener(
                new NoticeProjectionApplicationService(
                        new NoticeProjectionService(new ObjectMapper().findAndRegisterModules(), noticeService)
                )
        );
        UUID targetUserId = uuid(9);
        UUID postId = uuid(100);

        CommentPayload payload = new CommentPayload();
        payload.setTargetUserId(targetUserId);
        payload.setPostId(postId);

        listener.onContentEvent(new ContentContractEvent("evt-comment-1", ContentEventTypes.COMMENT_CREATED, payload));

        verify(noticeService).createNotice(eq(targetUserId), eq("comment"), contains("evt-comment-1"));
    }

    @Test
    void likeCreatedShouldCreateLikeNotice() {
        NoticeService noticeService = mock(NoticeService.class);
        NoticeProjectionListener listener = new NoticeProjectionListener(
                new NoticeProjectionApplicationService(
                        new NoticeProjectionService(new ObjectMapper().findAndRegisterModules(), noticeService)
                )
        );
        UUID entityUserId = uuid(8);
        UUID entityId = uuid(200);

        LikePayload payload = new LikePayload();
        payload.setEntityUserId(entityUserId);
        payload.setEntityId(entityId);

        listener.onSocialEvent(new SocialContractEvent("evt-like-1", SocialEventTypes.LIKE_CREATED, payload));

        verify(noticeService).createNotice(eq(entityUserId), eq("like"), contains("evt-like-1"));
    }
}
