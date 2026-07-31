package org.springframework.kafka.core;

public class KafkaTemplate {
    public Object send(String topic, String payload) {
        return "kafka-ok";
    }
}
