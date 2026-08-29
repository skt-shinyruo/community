package com.nowcoder.community.notice.application;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.notice.application.NoticeApplicationService.CreateNoticeCommand;
import com.nowcoder.community.notice.application.command.ProjectNoticeCommand;
import com.nowcoder.community.notice.domain.model.LikeNoticeProjectionState;
import com.nowcoder.community.notice.domain.model.NoticeProjection;
import com.nowcoder.community.notice.domain.model.NoticeProjectionContent;
import com.nowcoder.community.notice.domain.model.NoticeTopic;
import com.nowcoder.community.notice.domain.repository.LikeNoticeProjectionStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class NoticeProjectionApplicationService {

    private static final int COMMENT_PREVIEW_CODE_POINT_LIMIT = 240;

    private final JacksonJsonCodec jsonCodec;
    private final NoticeApplicationService noticeApplicationService;
    private final NoticePolicyProperties noticePolicyProperties;
    private final Optional<NoticeProjectionEventRecorder> noticeProjectionEventRecorder;
    private final Optional<LikeNoticeProjectionStateRepository> likeNoticeProjectionStateRepository;

    public NoticeProjectionApplicationService(
            JacksonJsonCodec jsonCodec,
            NoticeApplicationService noticeApplicationService,
            NoticePolicyProperties noticePolicyProperties,
            Optional<NoticeProjectionEventRecorder> noticeProjectionEventRecorder,
            Optional<LikeNoticeProjectionStateRepository> likeNoticeProjectionStateRepository
    ) {
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec must not be null");
        this.noticeApplicationService = Objects.requireNonNull(
                noticeApplicationService,
                "noticeApplicationService must not be null"
        );
        this.noticePolicyProperties = Objects.requireNonNull(
                noticePolicyProperties,
                "noticePolicyProperties must not be null"
        );
        this.noticeProjectionEventRecorder = Objects.requireNonNull(
                noticeProjectionEventRecorder,
                "noticeProjectionEventRecorder must not be null"
        );
        this.likeNoticeProjectionStateRepository = Objects.requireNonNull(
                likeNoticeProjectionStateRepository,
                "likeNoticeProjectionStateRepository must not be null"
        );
    }

    @Transactional
    public void projectReliably(ProjectNoticeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!noticePolicyProperties.isProjectionEnabled()) {
            throw new IllegalStateException("notice projection is paused");
        }
        requireSourceMetadata(command);
        if (command instanceof ProjectNoticeCommand.LikeCreated likeCreated) {
            projectLikeReliably(likeCreated, true);
            return;
        }
        if (command instanceof ProjectNoticeCommand.LikeRemoved likeRemoved) {
            projectLikeReliably(likeRemoved, false);
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
                            commentPreview(comment.content()),
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
                            like.relationKey(),
                            like.relationInstanceId()
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
        NoticeProjectionEventRecorder eventRecorder = noticeProjectionEventRecorder.orElse(null);
        if (eventRecorder != null && !eventRecorder.tryRecord(projection.sourceEventId())) {
            return;
        }
        createProjectedNotice(projection);
    }

    private void projectLikeReliably(ProjectNoticeCommand command, boolean active) {
        NoticeProjection projection = active ? toProjection(command) : null;
        if (active && !shouldProject(projection)) {
            return;
        }
        LikeNoticeProjectionState incoming = likeState(command, active);
        LikeNoticeProjectionStateRepository stateRepository = likeNoticeProjectionStateRepository.orElse(null);
        if (stateRepository == null) {
            throw new IllegalStateException("like notice projection state repository is required");
        }
        NoticeProjectionEventRecorder eventRecorder = noticeProjectionEventRecorder.orElse(null);
        if (eventRecorder != null && !eventRecorder.tryRecord(command.sourceEventId())) {
            return;
        }
        LikeNoticeProjectionState.Transition transition = stateRepository.advance(incoming);
        if (transition == LikeNoticeProjectionState.Transition.ACTIVATED) {
            noticeApplicationService.revokeLikeNotice(incoming.recipientUserId(), incoming.sourceRelationKey());
            createProjectedNotice(projection);
        } else if (transition == LikeNoticeProjectionState.Transition.DEACTIVATED) {
            noticeApplicationService.revokeLikeNotice(incoming.recipientUserId(), incoming.sourceRelationKey());
        }
    }

    private boolean shouldProject(NoticeProjection projection) {
        if (!noticePolicyProperties.getChannels().isInAppEnabled()) {
            return false;
        }
        return projection != null
                && projection.toUserId() != null
                && StringUtils.hasText(projection.topic())
                && projection.content() != null;
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

    private void requireSourceMetadata(ProjectNoticeCommand command) {
        requireSourceEventId(command.sourceEventId());
        if (command.sourceVersion() <= 0L) {
            throw new IllegalStateException("notice projection source version must be positive");
        }
    }

    private LikeNoticeProjectionState likeState(ProjectNoticeCommand command, boolean active) {
        if (command instanceof ProjectNoticeCommand.LikeCreated like) {
            return new LikeNoticeProjectionState(
                    like.entityUserId(),
                    like.relationKey(),
                    like.relationInstanceId(),
                    like.sourceVersion(),
                    active,
                    like.sourceEventId()
            );
        }
        ProjectNoticeCommand.LikeRemoved like = (ProjectNoticeCommand.LikeRemoved) command;
        return new LikeNoticeProjectionState(
                like.entityUserId(),
                like.relationKey(),
                like.relationInstanceId(),
                like.sourceVersion(),
                active,
                like.sourceEventId()
        );
    }

    private static String commentPreview(String content) {
        String value = content == null ? "" : content;
        if (value.codePointCount(0, value.length()) <= COMMENT_PREVIEW_CODE_POINT_LIMIT) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, COMMENT_PREVIEW_CODE_POINT_LIMIT));
    }
}
