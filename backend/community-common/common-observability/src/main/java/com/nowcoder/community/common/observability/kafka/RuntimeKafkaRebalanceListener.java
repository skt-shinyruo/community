package com.nowcoder.community.common.observability.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class RuntimeKafkaRebalanceListener implements ConsumerAwareRebalanceListener {

    private final KafkaRuntimeLogger logger;

    public RuntimeKafkaRebalanceListener(KafkaRuntimeLogger logger) {
        this.logger = logger;
    }

    @Override
    public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        log(consumer, "assigned", partitions);
    }

    @Override
    public void onPartitionsRevokedBeforeCommit(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        log(consumer, "revoked_before_commit", partitions);
    }

    @Override
    public void onPartitionsRevokedAfterCommit(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        log(consumer, "revoked_after_commit", partitions);
    }

    @Override
    public void onPartitionsLost(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        log(consumer, "lost", partitions);
    }

    private void log(Consumer<?, ?> consumer, String reason, Collection<TopicPartition> partitions) {
        if (partitions == null || partitions.isEmpty()) {
            return;
        }
        logger.logRebalance(groupId(consumer), reason, topicSummary(partitions), partitions.size());
    }

    private String groupId(Consumer<?, ?> consumer) {
        try {
            return consumer == null || consumer.groupMetadata() == null ? "-" : consumer.groupMetadata().groupId();
        } catch (RuntimeException ex) {
            return "-";
        }
    }

    private String topicSummary(Collection<TopicPartition> partitions) {
        Set<String> topics = new LinkedHashSet<>();
        for (TopicPartition partition : partitions) {
            topics.add(partition.topic());
            if (topics.size() > 1) {
                return "multiple";
            }
        }
        return topics.isEmpty() ? "-" : topics.iterator().next();
    }
}
