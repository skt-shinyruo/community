package com.nowcoder.community.notice.application;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.notice.application.command.CreateNoticeCommand;
import com.nowcoder.community.notice.application.command.ProjectNoticeCommand;
import com.nowcoder.community.notice.domain.model.NoticeProjection;
import com.nowcoder.community.notice.domain.model.NoticeProjectionContent;
import com.nowcoder.community.notice.domain.model.NoticeTopic;
import com.nowcoder.community.notice.domain.service.NoticeProjectionDomainService;
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

    @Transactional
    public void projectReliably(ProjectNoticeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!noticePolicyProperties.isProjectionEnabled()) {
            return;
        }
        if (command instanceof ProjectNoticeCommand.LikeRemoved likeRemoved) {
            revokeLikeNoticeReliably(likeRemoved);
            return;
        }
        projectReliably(toProjection(command));
    }

    private NoticeProjection toProjection(ProjectNoticeCommand command) {
        if (command instanceof ProjectNoticeCommand.CommentCreated comment) {
            return projection(
                    comment.sourceEventId(),
                    comment.sourceEventType(),
                    NoticeTopic.COMMENT,
                    comment.targetUserId(),
                    null,
                    new NoticeProjectionContent.Comment(
                            comment.commentId(),
                            comment.postId(),
                            comment.userId(),
                            comment.entityType(),
                            comment.entityId(),
                            comment.targetUserId(),
                            comment.content(),
                            comment.createTime()
                    )
            );
        }
        if (command instanceof ProjectNoticeCommand.ModerationApplied moderation) {
            return projection(
                    moderation.sourceEventId(),
                    moderation.sourceEventType(),
                    NoticeTopic.MODERATION,
                    moderation.toUserId(),
                    null,
                    new NoticeProjectionContent.Moderation(
                            moderation.reportId(),
                            moderation.kind(),
                            moderation.toUserId(),
                            moderation.actorUserId(),
                            moderation.targetType(),
                            moderation.targetId(),
                            moderation.action(),
                            moderation.reason(),
                            moderation.durationSeconds(),
                            moderation.createTime()
                    )
            );
        }
        if (command instanceof ProjectNoticeCommand.LikeCreated like) {
            return projection(
                    like.sourceEventId(),
                    like.sourceEventType(),
                    NoticeTopic.LIKE,
                    like.entityUserId(),
                    like.relationKey(),
                    new NoticeProjectionContent.Like(
                            like.actorUserId(),
                            like.entityType(),
                            like.entityId(),
                            like.entityUserId(),
                            like.postId(),
                            like.relationKey()
                    )
            );
        }
        if (command instanceof ProjectNoticeCommand.FollowCreated follow) {
            return projection(
                    follow.sourceEventId(),
                    follow.sourceEventType(),
                    NoticeTopic.FOLLOW,
                    follow.entityUserId(),
                    null,
                    new NoticeProjectionContent.Follow(
                            follow.actorUserId(),
                            follow.entityType(),
                            follow.entityId(),
                            follow.entityUserId(),
                            follow.createTime()
                    )
            );
        }
        throw new IllegalArgumentException("unsupported notice projection command: " + command.getClass().getName());
    }

    private void projectReliably(NoticeProjection projection) {
        if (!shouldProject(projection)) {
            return;
        }
        requireSourceEventId(projection.sourceEventId());
        if (noticeProjectionEventRecorder != null && !noticeProjectionEventRecorder.tryRecord(projection.sourceEventId())) {
            return;
        }
        createProjectedNotice(projection);
    }

    private void revokeLikeNoticeReliably(ProjectNoticeCommand.LikeRemoved command) {
        requireSourceEventId(command.sourceEventId());
        if (noticeProjectionEventRecorder != null && !noticeProjectionEventRecorder.tryRecord(command.sourceEventId())) {
            return;
        }
        noticeApplicationService.revokeLikeNotice(command.entityUserId(), command.relationKey());
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
                    "payload", projection.content()
            ));
            noticeApplicationService.createNotice(new CreateNoticeCommand(
                    projection.toUserId(),
                    projection.topic(),
                    contentJson,
                    projection.sourceEventType(),
                    projection.sourceRelationKey()
            ));
        } catch (JsonCodecException e) {
            throw new IllegalStateException("notice payload serialization failed: " + projection.sourceEventType(), e);
        }
    }

    private NoticeProjection projection(
            String eventId,
            String eventType,
            String noticeTopic,
            UUID toUserId,
            String sourceRelationKey,
            NoticeProjectionContent content
    ) {
        if (toUserId == null) {
            return null;
        }
        return new NoticeProjection(toUserId, noticeTopic, eventId, eventType, sourceRelationKey, content);
    }

    private void requireSourceEventId(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            throw new IllegalStateException("notice projection source event id is blank");
        }
    }
}
