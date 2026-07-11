package com.nowcoder.community.im.infrastructure.event;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.im.application.ImPolicyProjectionEvent;
import com.nowcoder.community.im.application.ImPolicyProjectionOutboxPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class JdbcImPolicyProjectionOutboxAdapter implements ImPolicyProjectionOutboxPort {

    private final JdbcOutboxEventStore store;
    private final JsonCodec jsonCodec;
    private final String topic;

    public JdbcImPolicyProjectionOutboxAdapter(
            JdbcOutboxEventStore store,
            JsonCodec jsonCodec,
            @Value("${im.policy.outbox.topic:projection.im.policy}") String topic
    ) {
        this.store = store;
        this.jsonCodec = jsonCodec;
        this.topic = topic;
    }

    @Override
    public void enqueue(ImPolicyProjectionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("IM policy projection event must not be null");
        }
        try {
            String eventId = buildEventId(event);
            store.enqueue(eventId, topic, event.primaryUserId().toString(), jsonCodec.toJson(event));
        } catch (JsonCodecException e) {
            throw new IllegalStateException("im policy outbox payload 序列化失败", e);
        }
    }

    private String buildEventId(ImPolicyProjectionEvent event) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((
                    event.sourceDomain() + "\u0000" + event.sourceEventId() + "\u0000" + event.kind()
            ).getBytes(StandardCharsets.UTF_8));
            String suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return ("USER_POLICY".equals(event.kind()) ? "ip:u:" : "ip:s:") + suffix;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
