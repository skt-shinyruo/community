package com.nowcoder.community.growth.application;

public interface TaskProgressKafkaDispatchPort {

    void send(String topic, String key, Object payload);
}
