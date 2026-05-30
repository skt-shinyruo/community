package com.nowcoder.community.common.event;

import com.nowcoder.community.common.trace.TraceIdCodec;

import java.time.Instant;
import java.util.UUID;

public class EventEnvelope<T> {

    private String eventId;
    private String traceId;
    private String type;
    private int version;
    private Instant occurredAt;
    private String producer;
    private T payload;

    public static <T> EventEnvelope<T> of(String type, int version, String producer, T payload) {
        return of(type, version, producer, payload, TraceIdCodec.generateTraceId());
    }

    /**
     * 显式指定 traceId 的工厂方法（推荐）：避免 contracts 读取 ThreadLocal 等运行期上下文。
     */
    public static <T> EventEnvelope<T> of(String type, int version, String producer, T payload, String traceId) {
        EventEnvelope<T> e = new EventEnvelope<>();
        e.eventId = UUID.randomUUID().toString().replace("-", "");
        e.traceId = traceId == null || traceId.isBlank() ? TraceIdCodec.generateTraceId() : traceId.trim();
        e.type = type;
        e.version = version;
        e.occurredAt = Instant.now();
        e.producer = producer;
        e.payload = payload;
        return e;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getProducer() {
        return producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }
}
