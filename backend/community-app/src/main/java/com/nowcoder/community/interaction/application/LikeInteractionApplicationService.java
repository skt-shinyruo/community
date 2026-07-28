package com.nowcoder.community.interaction.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.content.api.model.ResolvedContentRef;
import com.nowcoder.community.content.api.query.ContentEntityQueryApi;
import com.nowcoder.community.social.api.action.SocialLikeActionApi;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.COMMENT;
import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class LikeInteractionApplicationService {

    private final UserLookupQueryApi userLookupQueryApi;
    private final ContentEntityQueryApi contentEntityQueryApi;
    private final SocialLikeActionApi socialLikeActionApi;

    public LikeInteractionApplicationService(
            UserLookupQueryApi userLookupQueryApi,
            ContentEntityQueryApi contentEntityQueryApi,
            SocialLikeActionApi socialLikeActionApi
    ) {
        this.userLookupQueryApi = userLookupQueryApi;
        this.contentEntityQueryApi = contentEntityQueryApi;
        this.socialLikeActionApi = socialLikeActionApi;
    }

    public LikeResult setLike(SetLikeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireValidTarget(command);
        ResolvedTarget resolvedTarget = resolveTarget(command);
        SocialLikeActionApi.LikeResult result = Objects.requireNonNull(
                socialLikeActionApi.setLike(new SocialLikeActionApi.SetLikeCommand(
                        command.actorUserId(),
                        command.entityType(),
                        command.entityId(),
                        command.liked(),
                        resolvedTarget.entityUserId(),
                        resolvedTarget.postId()
                )),
                "social like result must not be null"
        );
        return new LikeResult(result.liked(), result.likeCount());
    }

    private ResolvedTarget resolveTarget(SetLikeCommand command) {
        if (command.entityType() == USER) {
            UserSummaryView user = userLookupQueryApi.getSummaryById(command.entityId());
            if (user == null || user.id() == null) {
                throw new BusinessException(NOT_FOUND, "like target user not found");
            }
            return new ResolvedTarget(user.id(), null);
        }
        ResolvedContentRef content = contentEntityQueryApi.resolve(command.entityType(), command.entityId());
        if (content == null || content.entityUserId() == null || content.postId() == null) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "like target resolution incomplete");
        }
        return new ResolvedTarget(content.entityUserId(), content.postId());
    }

    private void requireValidTarget(SetLikeCommand command) {
        if (command.actorUserId() == null
                || command.entityId() == null
                || (command.entityType() != USER && command.entityType() != POST && command.entityType() != COMMENT)) {
            throw new BusinessException(INVALID_ARGUMENT, "like target invalid");
        }
    }

    public record SetLikeCommand(UUID actorUserId, int entityType, UUID entityId, Boolean liked) {
    }

    public record LikeResult(boolean liked, long likeCount) {
    }

    private record ResolvedTarget(UUID entityUserId, UUID postId) {
    }
}
