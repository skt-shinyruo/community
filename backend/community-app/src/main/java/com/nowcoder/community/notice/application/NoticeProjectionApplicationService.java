package com.nowcoder.community.notice.application;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.content.contracts.event.CommentPayload;
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
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class NoticeProjectionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(NoticeProjectionApplicationService.class);

    private final JsonCodec jsonCodec;
    private final NoticeApplicationService noticeApplicationService;
    private final NoticeProjectionDomainService noticeProjectionDomainService;
    private final NoticePolicyProperties noticePolicyProperties;
    private final NoticeProjectionEventRecorder noticeProjectionEventRecorder;

    @Autowired
    public NoticeProjectionApplicationService(
            JsonCodec jsonCodec,
            NoticeApplicationService noticeApplicationService,
            NoticePolicyProperties noticePolicyProperties,
            ObjectProvider<NoticeProjectionEventRecorder> noticeProjectionEventRecorderProvider
    ) {
        this(
                jsonCodec,
                noticeApplicationService,
                new NoticeProjectionDomainService(),
                noticePolicyProperties,
                noticeProjectionEventRecorderProvider == null ? null : noticeProjectionEventRecorderProvider.getIfAvailable()
        );
    }

    public NoticeProjectionApplicationService(
            JsonCodec jsonCodec,
            NoticeApplicationService noticeApplicationService,
            NoticePolicyProperties noticePolicyProperties
    ) {
        this(jsonCodec, noticeApplicationService, new NoticeProjectionDomainService(), noticePolicyProperties, null);
    }

    public NoticeProjectionApplicationService(JsonCodec jsonCodec, NoticeApplicationService noticeApplicationService) {
        this(jsonCodec, noticeApplicationService, new NoticeProjectionDomainService(), new NoticePolicyProperties(), null);
    }

    NoticeProjectionApplicationService(
            JsonCodec jsonCodec,
            NoticeApplicationService noticeApplicationService,
            NoticeProjectionDomainService noticeProjectionDomainService,
            NoticePolicyProperties noticePolicyProperties
    ) {
        this(jsonCodec, noticeApplicationService, noticeProjectionDomainService, noticePolicyProperties, null);
    }

    NoticeProjectionApplicationService(
            JsonCodec jsonCodec,
            NoticeApplicationService noticeApplicationService,
            NoticeProjectionDomainService noticeProjectionDomainService,
            NoticePolicyProperties noticePolicyProperties,
            NoticeProjectionEventRecorder noticeProjectionEventRecorder
    ) {
        this.jsonCodec = jsonCodec;
        this.noticeApplicationService = noticeApplicationService;
        this.noticeProjectionDomainService = noticeProjectionDomainService;
        this.noticePolicyProperties = noticePolicyProperties == null ? new NoticePolicyProperties() : noticePolicyProperties;
        this.noticeProjectionEventRecorder = noticeProjectionEventRecorder;
    }

    public void projectContentEvent(ProjectContentNoticeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!noticePolicyProperties.isProjectionEnabled()) {
            return;
        }
        try {
            project(commandForContentEvent(command));
        } catch (RuntimeException e) {
            log.warn("[notice] projection failed after commit (eventId={}, type={}): {}", command.sourceEventId(), command.eventType(), e.toString());
        }
    }

    public void projectSocialEvent(ProjectSocialNoticeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!noticePolicyProperties.isProjectionEnabled()) {
            return;
        }
        try {
            project(commandForSocialEvent(command));
        } catch (RuntimeException e) {
            log.warn("[notice] projection failed after commit (eventId={}, type={}): {}", command.sourceEventId(), command.eventType(), e.toString());
        }
    }

    @Transactional
    public void projectContentEventReliably(ProjectContentNoticeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!noticePolicyProperties.isProjectionEnabled()) {
            return;
        }
        projectReliably(commandForContentEvent(command));
    }

    @Transactional
    public void projectSocialEventReliably(ProjectSocialNoticeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!noticePolicyProperties.isProjectionEnabled()) {
            return;
        }
        if (SocialEventTypes.LIKE_REMOVED.equals(command.eventType()) && command.payload() instanceof LikePayload payload) {
            if (!StringUtils.hasText(command.sourceEventId())) {
                throw new IllegalStateException("notice projection source event id is blank");
            }
            if (noticeProjectionEventRecorder != null && !noticeProjectionEventRecorder.tryRecord(command.sourceEventId())) {
                return;
            }
            revokeProjectedLikeNotice(payload);
            return;
        }
        projectReliably(commandForSocialEvent(command));
    }

    NoticeProjection commandForContentEvent(ProjectContentNoticeCommand command) {
        if (ContentEventTypes.COMMENT_CREATED.equals(command.eventType()) && command.payload() instanceof CommentPayload payload) {
            return projection(command.sourceEventId(), command.eventType(), NoticeTopic.COMMENT, payload.getTargetUserId(), payload);
        }
        if (ContentEventTypes.MODERATION_ACTION_APPLIED.equals(command.eventType()) && command.payload() instanceof ModerationPayload payload) {
            return projection(command.sourceEventId(), command.eventType(), NoticeTopic.MODERATION, payload.getToUserId(), payload);
        }
        return null;
    }

    NoticeProjection commandForSocialEvent(ProjectSocialNoticeCommand command) {
        if (SocialEventTypes.LIKE_CREATED.equals(command.eventType()) && command.payload() instanceof LikePayload payload) {
            return projection(command.sourceEventId(), command.eventType(), NoticeTopic.LIKE, payload.getEntityUserId(), payload);
        }
        if (SocialEventTypes.FOLLOW_CREATED.equals(command.eventType()) && command.payload() instanceof FollowPayload payload) {
            return projection(command.sourceEventId(), command.eventType(), NoticeTopic.FOLLOW, payload.getEntityUserId(), payload);
        }
        return null;
    }

    private void project(NoticeProjection projection) {
        if (!shouldProject(projection)) {
            return;
        }
        createProjectedNotice(projection);
    }

    private void projectReliably(NoticeProjection projection) {
        if (!shouldProject(projection)) {
            return;
        }
        if (!StringUtils.hasText(projection.sourceEventId())) {
            throw new IllegalStateException("notice projection source event id is blank");
        }
        if (noticeProjectionEventRecorder != null && !noticeProjectionEventRecorder.tryRecord(projection.sourceEventId())) {
            return;
        }
        createProjectedNotice(projection);
    }

    private boolean shouldProject(NoticeProjection projection) {
        if (!noticePolicyProperties.isProjectionEnabled()) {
            return false;
        }
        if (!noticePolicyProperties.getChannels().isInAppEnabled()) {
            return false;
        }
        return noticeProjectionDomainService.shouldProject(projection);
    }

    private void createProjectedNotice(NoticeProjection projection) {
        try {
            String contentJson = jsonCodec.toJson(Map.of(
                    "eventId", projection.sourceEventId(),
                    "type", projection.sourceEventType(),
                    "payload", projection.payload()
            ));
            noticeApplicationService.createNotice(new CreateNoticeCommand(
                    projection.toUserId(),
                    projection.topic(),
                    contentJson,
                    projection.sourceEventType(),
                    relationKey(projection.payload())
            ));
        } catch (JsonCodecException e) {
            throw new IllegalStateException("notice payload serialization failed: " + projection.sourceEventType(), e);
        }
    }

    private NoticeProjection projection(String eventId, String eventType, String noticeTopic, UUID toUserId, Object payload) {
        if (toUserId == null) {
            return null;
        }
        return new NoticeProjection(toUserId, noticeTopic, eventId, eventType, payload);
    }

    private void revokeProjectedLikeNotice(LikePayload payload) {
        if (payload == null) {
            return;
        }
        noticeApplicationService.revokeLikeNotice(payload.getEntityUserId(), payload.getRelationKey());
    }

    private String relationKey(Object payload) {
        if (payload instanceof LikePayload likePayload) {
            return likePayload.getRelationKey();
        }
        return null;
    }
}
