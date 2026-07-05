package com.nowcoder.community.growth.infrastructure.event;

import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.growth.application.TaskProgressIntegrationEventDispatcher;
import com.nowcoder.community.social.contracts.event.LikePayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionException;

@Component
@ConditionalOnClass(KafkaTemplate.class)
public class TaskProgressKafkaSenderAdapter implements TaskProgressIntegrationEventDispatcher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String postPublishedTopic;
    private final String commentCreatedTopic;
    private final String likeCreatedTopic;
    private final String likeRemovedTopic;

    public TaskProgressKafkaSenderAdapter(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${growth.task.kafka.topics.post-published:growth.task.post-published}") String postPublishedTopic,
            @Value("${growth.task.kafka.topics.comment-created:growth.task.comment-created}") String commentCreatedTopic,
            @Value("${growth.task.kafka.topics.like-created:growth.task.like-created}") String likeCreatedTopic,
            @Value("${growth.task.kafka.topics.like-removed:growth.task.like-removed}") String likeRemovedTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.postPublishedTopic = postPublishedTopic;
        this.commentCreatedTopic = commentCreatedTopic;
        this.likeCreatedTopic = likeCreatedTopic;
        this.likeRemovedTopic = likeRemovedTopic;
    }

    @Override
    public void dispatchPostPublished(String eventKey, PostPayload payload) {
        send(postPublishedTopic, eventKey, payload);
    }

    @Override
    public void dispatchCommentCreated(String eventKey, CommentPayload payload) {
        send(commentCreatedTopic, eventKey, payload);
    }

    @Override
    public void dispatchLikeCreated(String eventKey, LikePayload payload) {
        send(likeCreatedTopic, eventKey, payload);
    }

    @Override
    public void dispatchLikeRemoved(String eventKey, LikePayload payload) {
        send(likeRemovedTopic, eventKey, payload);
    }

    private void send(String topic, String eventKey, Object payload) {
        try {
            TraceKafkaSender.send(kafkaTemplate, topic, eventKey, payload).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("growth task kafka publish failed: " + topic, cause);
        }
    }
}
