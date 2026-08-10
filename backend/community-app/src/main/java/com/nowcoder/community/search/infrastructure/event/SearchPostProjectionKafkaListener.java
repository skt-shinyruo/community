package com.nowcoder.community.search.infrastructure.event;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentContractEventCodec;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ContentTypedEvent;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.content.contracts.event.PostScorePayload;
import com.nowcoder.community.search.application.SearchPostProjectionApplicationService;
import com.nowcoder.community.search.application.SearchPostProjectionApplicationService.ProjectPostCommand;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SearchPostProjectionKafkaListener {

    private final ContentContractEventCodec contractEventCodec;
    private final SearchPostProjectionApplicationService searchPostProjectionApplicationService;

    public SearchPostProjectionKafkaListener(
            ContentContractEventCodec contractEventCodec,
            SearchPostProjectionApplicationService searchPostProjectionApplicationService
    ) {
        this.contractEventCodec = contractEventCodec;
        this.searchPostProjectionApplicationService = searchPostProjectionApplicationService;
    }

    @KafkaListener(
            topics = "${content.events.kafka-topic:content.events}",
            groupId = "${search.kafka.consumer.group-id:search-post-projection}",
            concurrency = "${search.kafka.consumer.concurrency:3}"
    )
    public void onContentEvent(ContentContractEvent event) {
        if (event == null || !isPostProjectionEvent(event.type())) {
            return;
        }
        requireSourceMetadata(event);
        ContentTypedEvent typedEvent;
        try {
            typedEvent = contractEventCodec.decode(event);
        } catch (RuntimeException error) {
            throw malformed(event);
        }
        if (ContentEventTypes.POST_SCORE_UPDATED.equals(event.type())) {
            PostScorePayload payload = scorePayload(typedEvent);
            if (payload == null
                    || payload.postId() == null
                    || payload.aggregateVersion() <= 0L
                    || payload.scoreVersion() <= 0L
                    || payload.scoreVersion() != event.version()) {
                throw malformed(event);
            }
            searchPostProjectionApplicationService.projectPost(new ProjectPostCommand(
                    payload.postId(),
                    event.eventId(),
                    payload.aggregateVersion()
            ));
            return;
        }
        PostPayload payload = postPayload(typedEvent);
        if (payload == null || payload.getPostId() == null) {
            throw malformed(event);
        }
        searchPostProjectionApplicationService.projectPost(new ProjectPostCommand(
                payload.getPostId(),
                event.eventId(),
                event.version()
        ));
    }

    private void requireSourceMetadata(ContentContractEvent event) {
        if (!StringUtils.hasText(event.eventId()) || event.occurredAt() == null || event.version() <= 0L) {
            throw malformed(event);
        }
    }

    private IllegalArgumentException malformed(ContentContractEvent event) {
        return new IllegalArgumentException(
                "invalid recognized content event: type=" + event.type() + ", eventId=" + event.eventId());
    }

    private boolean isPostProjectionEvent(String type) {
        return ContentEventTypes.POST_PUBLISHED.equals(type)
                || ContentEventTypes.POST_UPDATED.equals(type)
                || ContentEventTypes.POST_SCORE_UPDATED.equals(type)
                || ContentEventTypes.POST_DELETED.equals(type);
    }

    private PostPayload postPayload(ContentTypedEvent event) {
        if (event instanceof ContentTypedEvent.PostPublished value) {
            return value.payload();
        }
        if (event instanceof ContentTypedEvent.PostUpdated value) {
            return value.payload();
        }
        if (event instanceof ContentTypedEvent.PostDeleted value) {
            return value.payload();
        }
        throw new IllegalArgumentException("unsupported search content event variant: " + event.getClass().getName());
    }

    private PostScorePayload scorePayload(ContentTypedEvent event) {
        if (event instanceof ContentTypedEvent.PostScoreUpdated value) {
            return value.payload();
        }
        throw new IllegalArgumentException("unsupported search score event variant: " + event.getClass().getName());
    }
}
