package com.nowcoder.community.notice.application;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.notice.application.command.CreateNoticeCommand;
import com.nowcoder.community.notice.application.command.ProjectContentNoticeCommand;
import com.nowcoder.community.notice.application.command.ProjectSocialNoticeCommand;
import com.nowcoder.community.notice.domain.model.NoticeProjection;
import com.nowcoder.community.notice.domain.model.NoticeTopic;
import com.nowcoder.community.notice.domain.service.NoticeProjectionDomainService;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class NoticeProjectionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(NoticeProjectionApplicationService.class);

    private final JsonCodec jsonCodec;
    private final NoticeApplicationService noticeApplicationService;
    private final NoticeProjectionDomainService noticeProjectionDomainService;
    private final NoticePolicyProperties noticePolicyProperties;

    @Autowired
    public NoticeProjectionApplicationService(
            JsonCodec jsonCodec,
            NoticeApplicationService noticeApplicationService,
            NoticePolicyProperties noticePolicyProperties
    ) {
        this(jsonCodec, noticeApplicationService, new NoticeProjectionDomainService(), noticePolicyProperties);
    }

    public NoticeProjectionApplicationService(JsonCodec jsonCodec, NoticeApplicationService noticeApplicationService) {
        this(jsonCodec, noticeApplicationService, new NoticeProjectionDomainService(), new NoticePolicyProperties());
    }

    NoticeProjectionApplicationService(
            JsonCodec jsonCodec,
            NoticeApplicationService noticeApplicationService,
            NoticeProjectionDomainService noticeProjectionDomainService,
            NoticePolicyProperties noticePolicyProperties
    ) {
        this.jsonCodec = jsonCodec;
        this.noticeApplicationService = noticeApplicationService;
        this.noticeProjectionDomainService = noticeProjectionDomainService;
        this.noticePolicyProperties = noticePolicyProperties == null ? new NoticePolicyProperties() : noticePolicyProperties;
    }

    public void projectContentEvent(ContentContractEvent event) {
        projectContentEvent(new ProjectContentNoticeCommand(event));
    }

    public void projectContentEvent(ProjectContentNoticeCommand command) {
        ContentContractEvent event = command == null ? null : command.event();
        if (event == null) {
            return;
        }
        try {
            project(commandForContentEvent(event));
        } catch (RuntimeException e) {
            log.warn("[notice] projection failed after commit (eventId={}, type={}): {}", event.eventId(), event.type(), e.toString());
        }
    }

    public void projectSocialEvent(SocialContractEvent event) {
        projectSocialEvent(new ProjectSocialNoticeCommand(event));
    }

    public void projectSocialEvent(ProjectSocialNoticeCommand command) {
        SocialContractEvent event = command == null ? null : command.event();
        if (event == null) {
            return;
        }
        try {
            project(commandForSocialEvent(event));
        } catch (RuntimeException e) {
            log.warn("[notice] projection failed after commit (eventId={}, type={}): {}", event.eventId(), event.type(), e.toString());
        }
    }

    NoticeProjection commandForContentEvent(ContentContractEvent event) {
        if (event == null) {
            return null;
        }
        if (ContentEventTypes.COMMENT_CREATED.equals(event.type()) && event.payload() instanceof CommentPayload payload) {
            return projection(event.eventId(), event.type(), NoticeTopic.COMMENT, payload.getTargetUserId(), payload);
        }
        if (ContentEventTypes.MODERATION_ACTION_APPLIED.equals(event.type()) && event.payload() instanceof ModerationPayload payload) {
            return projection(event.eventId(), event.type(), NoticeTopic.MODERATION, payload.getToUserId(), payload);
        }
        return null;
    }

    NoticeProjection commandForSocialEvent(SocialContractEvent event) {
        if (event == null) {
            return null;
        }
        if (SocialEventTypes.LIKE_CREATED.equals(event.type()) && event.payload() instanceof LikePayload payload) {
            return projection(event.eventId(), event.type(), NoticeTopic.LIKE, payload.getEntityUserId(), payload);
        }
        if (SocialEventTypes.FOLLOW_CREATED.equals(event.type()) && event.payload() instanceof FollowPayload payload) {
            return projection(event.eventId(), event.type(), NoticeTopic.FOLLOW, payload.getEntityUserId(), payload);
        }
        return null;
    }

    private void project(NoticeProjection projection) {
        if (!noticePolicyProperties.getChannels().isInAppEnabled()) {
            return;
        }
        if (!noticeProjectionDomainService.shouldProject(projection)) {
            return;
        }
        try {
            String contentJson = jsonCodec.toJson(Map.of(
                    "eventId", projection.sourceEventId(),
                    "type", projection.sourceEventType(),
                    "payload", projection.payload()
            ));
            noticeApplicationService.createNotice(new CreateNoticeCommand(projection.toUserId(), projection.topic(), contentJson));
        } catch (JsonCodecException e) {
            throw new IllegalStateException("notice payload serialization failed: " + projection.sourceEventType(), e);
        }
    }

    private NoticeProjection projection(String eventId, String eventType, String topic, UUID toUserId, Object payload) {
        if (toUserId == null) {
            return null;
        }
        return new NoticeProjection(toUserId, topic, eventId, eventType, payload);
    }
}
