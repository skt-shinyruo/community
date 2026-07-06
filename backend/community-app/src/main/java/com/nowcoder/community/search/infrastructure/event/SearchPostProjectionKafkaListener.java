package com.nowcoder.community.search.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.search.application.SearchPostProjectionApplicationService;
import com.nowcoder.community.search.application.command.ProjectPostOutboxCommand;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SearchPostProjectionKafkaListener {

    private final JsonCodec jsonCodec;
    private final SearchPostProjectionApplicationService searchPostProjectionApplicationService;

    public SearchPostProjectionKafkaListener(
            JsonCodec jsonCodec,
            SearchPostProjectionApplicationService searchPostProjectionApplicationService
    ) {
        this.jsonCodec = jsonCodec;
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
        PostPayload payload = normalizePostPayload(event.payload());
        if (payload == null || payload.getPostId() == null) {
            return;
        }
        searchPostProjectionApplicationService.projectPostFromOutbox(new ProjectPostOutboxCommand(
                payload.getPostId(),
                event.eventId(),
                event.version()
        ));
    }

    private boolean isPostProjectionEvent(String type) {
        return ContentEventTypes.POST_PUBLISHED.equals(type)
                || ContentEventTypes.POST_UPDATED.equals(type)
                || ContentEventTypes.POST_DELETED.equals(type);
    }

    private PostPayload normalizePostPayload(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof PostPayload postPayload) {
            return postPayload;
        }
        JsonNode node = jsonCodec.valueToTree(payload);
        return jsonCodec.treeToValue(node, PostPayload.class);
    }
}
