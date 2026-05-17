package com.nowcoder.community.common.spring.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "community.kafka-policy")
public class KafkaPolicyProperties {

    private final Retry retry = new Retry();
    private final Dlq dlq = new Dlq();
    private final Producer producer = new Producer();

    public Retry getRetry() {
        return retry;
    }

    public Dlq getDlq() {
        return dlq;
    }

    public Producer getProducer() {
        return producer;
    }

    public static class Retry {

        private int maxAttempts = 3;
        private Duration baseBackoff = Duration.ofSeconds(1);
        private Duration maxBackoff = Duration.ofSeconds(30);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = Math.max(1, maxAttempts);
        }

        public Duration getBaseBackoff() {
            return baseBackoff;
        }

        public void setBaseBackoff(Duration baseBackoff) {
            this.baseBackoff = positiveOrDefault(baseBackoff, Duration.ofSeconds(1));
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = positiveOrDefault(maxBackoff, Duration.ofSeconds(30));
        }

        private Duration positiveOrDefault(Duration value, Duration fallback) {
            return value == null || value.isNegative() || value.isZero() ? fallback : value;
        }
    }

    public static class Dlq {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Producer {

        private String acks = "all";
        private boolean enableIdempotence = true;
        private int maxInFlightRequests = 5;
        private int metadataMaxAgeMs = 1000;
        private int reconnectBackoffMs = 100;
        private int reconnectBackoffMaxMs = 1000;
        private int requestTimeoutMs = 3000;
        private int deliveryTimeoutMs = 5000;
        private int maxBlockMs = 5000;

        public String getAcks() {
            return acks;
        }

        public void setAcks(String acks) {
            this.acks = positiveTextOrDefault(acks, "all");
        }

        public boolean isEnableIdempotence() {
            return enableIdempotence;
        }

        public void setEnableIdempotence(boolean enableIdempotence) {
            this.enableIdempotence = enableIdempotence;
        }

        public int getMaxInFlightRequests() {
            return maxInFlightRequests;
        }

        public void setMaxInFlightRequests(int maxInFlightRequests) {
            this.maxInFlightRequests = Math.max(1, maxInFlightRequests);
        }

        public int getMetadataMaxAgeMs() {
            return metadataMaxAgeMs;
        }

        public void setMetadataMaxAgeMs(int metadataMaxAgeMs) {
            this.metadataMaxAgeMs = Math.max(1, metadataMaxAgeMs);
        }

        public int getReconnectBackoffMs() {
            return reconnectBackoffMs;
        }

        public void setReconnectBackoffMs(int reconnectBackoffMs) {
            this.reconnectBackoffMs = Math.max(1, reconnectBackoffMs);
        }

        public int getReconnectBackoffMaxMs() {
            return reconnectBackoffMaxMs;
        }

        public void setReconnectBackoffMaxMs(int reconnectBackoffMaxMs) {
            this.reconnectBackoffMaxMs = Math.max(1, reconnectBackoffMaxMs);
        }

        public int getRequestTimeoutMs() {
            return requestTimeoutMs;
        }

        public void setRequestTimeoutMs(int requestTimeoutMs) {
            this.requestTimeoutMs = Math.max(1, requestTimeoutMs);
        }

        public int getDeliveryTimeoutMs() {
            return deliveryTimeoutMs;
        }

        public void setDeliveryTimeoutMs(int deliveryTimeoutMs) {
            this.deliveryTimeoutMs = Math.max(1, deliveryTimeoutMs);
        }

        public int getMaxBlockMs() {
            return maxBlockMs;
        }

        public void setMaxBlockMs(int maxBlockMs) {
            this.maxBlockMs = Math.max(1, maxBlockMs);
        }

        private String positiveTextOrDefault(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }
    }
}
