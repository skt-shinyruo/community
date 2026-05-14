package com.nowcoder.community.common.observability.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.support.ProducerListener;

public class RuntimeKafkaProducerListener implements ProducerListener<Object, Object> {

    private final KafkaRuntimeLogger logger;

    public RuntimeKafkaProducerListener(KafkaRuntimeLogger logger) {
        this.logger = logger;
    }

    @Override
    public void onError(ProducerRecord<Object, Object> producerRecord, RecordMetadata recordMetadata, Exception exception) {
        String topic = producerRecord == null ? "-" : producerRecord.topic();
        Integer partition = producerRecord == null ? null : producerRecord.partition();
        if (partition == null && recordMetadata != null) {
            partition = recordMetadata.partition();
        }
        logger.logProducerError(topic, partition, exception);
    }
}
