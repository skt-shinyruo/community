package com.nowcoder.community.social.infrastructure.event;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentContractEventCodec;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ContentTypedEvent;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.social.application.LikeApplicationService;
import com.nowcoder.community.social.application.command.CleanupDeletedContentLikesCommand;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.COMMENT;
import static com.nowcoder.community.common.constants.EntityTypes.POST;

@Component
public class SocialContentDeletionKafkaListener {

    private final ContentContractEventCodec contractEventCodec;
    private final LikeApplicationService likeApplicationService;

    public SocialContentDeletionKafkaListener(
            ContentContractEventCodec contractEventCodec,
            LikeApplicationService likeApplicationService
    ) {
        this.contractEventCodec = contractEventCodec;
        this.likeApplicationService = likeApplicationService;
    }

    @KafkaListener(
            topics = "${content.events.kafka-topic:content.events}",
            groupId = "${social.content-deletion.kafka.consumer.group-id:social-content-deletion}",
            concurrency = "${social.content-deletion.kafka.consumer.concurrency:3}"
    )
    public void onContentEvent(ContentContractEvent event) {
        if (event == null || !isContentDeletion(event.type())) {
            return;
        }
        requireSourceMetadata(event);
        ContentTypedEvent typedEvent;
        try {
            typedEvent = contractEventCodec.decode(event);
        } catch (RuntimeException error) {
            throw malformed(event, error);
        }
        UUID entityId = typedEvent instanceof ContentTypedEvent.PostDeleted value
                ? postId(value.payload())
                : commentId(((ContentTypedEvent.CommentDeleted) typedEvent).payload());
        if (entityId == null) {
            throw malformed(event, null);
        }
        int entityType = ContentEventTypes.POST_DELETED.equals(event.type()) ? POST : COMMENT;
        likeApplicationService.cleanupDeletedContentLikes(new CleanupDeletedContentLikesCommand(
                entityType,
                entityId,
                event.eventId(),
                event.version(),
                event.occurredAt()
        ));
    }

    private UUID postId(PostPayload payload) {
        return payload == null ? null : payload.getPostId();
    }

    private UUID commentId(CommentPayload payload) {
        return payload == null ? null : payload.getCommentId();
    }

    private void requireSourceMetadata(ContentContractEvent event) {
        if (!StringUtils.hasText(event.eventId()) || event.occurredAt() == null || event.version() <= 0L) {
            throw malformed(event, null);
        }
    }

    private boolean isContentDeletion(String type) {
        return ContentEventTypes.POST_DELETED.equals(type)
                || ContentEventTypes.COMMENT_DELETED.equals(type);
    }

    private IllegalArgumentException malformed(ContentContractEvent event, Throwable cause) {
        String message = "invalid recognized content deletion event: type="
                + event.type()
                + ", eventId="
                + event.eventId();
        return cause == null ? new IllegalArgumentException(message) : new IllegalArgumentException(message, cause);
    }
}
