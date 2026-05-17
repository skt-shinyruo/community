package com.nowcoder.community.common.spring.policy;

import java.time.Duration;

public class KafkaPolicyDecisions {

    private final KafkaPolicyProperties properties;

    public KafkaPolicyDecisions(KafkaPolicyProperties properties) {
        this.properties = properties == null ? new KafkaPolicyProperties() : properties;
    }

    public int retryMaxAttempts() {
        return properties.getRetry().getMaxAttempts();
    }

    public Duration retryBaseBackoff() {
        return properties.getRetry().getBaseBackoff();
    }

    public Duration retryMaxBackoff() {
        return properties.getRetry().getMaxBackoff();
    }

    public boolean dlqEnabled() {
        return properties.getDlq().isEnabled();
    }

    public boolean producerIdempotenceEnabled() {
        return properties.getProducer().isEnableIdempotence();
    }

    public int producerMaxInFlightRequests() {
        return properties.getProducer().getMaxInFlightRequests();
    }
}
