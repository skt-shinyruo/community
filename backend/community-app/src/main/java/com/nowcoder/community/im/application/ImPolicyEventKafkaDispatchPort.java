package com.nowcoder.community.im.application;

public interface ImPolicyEventKafkaDispatchPort {

    void send(String topic, String key, Object event);
}
