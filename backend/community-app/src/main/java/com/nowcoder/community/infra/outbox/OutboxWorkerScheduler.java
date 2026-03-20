package com.nowcoder.community.infra.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring scheduler wrapper for {@link OutboxWorker}.
 */
public class OutboxWorkerScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorkerScheduler.class);

    private final OutboxWorker worker;
    private final OutboxProperties properties;

    public OutboxWorkerScheduler(
            JdbcOutboxEventStore store,
            ObjectProvider<List<OutboxHandler>> handlersProvider,
            OutboxProperties properties,
            Clock clock
    ) {
        List<OutboxHandler> handlers = handlersProvider == null ? null : handlersProvider.getIfAvailable();
        Map<String, OutboxHandler> handlerMap = new HashMap<>();
        if (handlers != null) {
            for (OutboxHandler handler : handlers) {
                if (handler == null || handler.topic() == null || handler.topic().isBlank()) {
                    continue;
                }
                handlerMap.put(handler.topic(), handler);
            }
        }

        this.properties = properties == null ? new OutboxProperties() : properties;
        this.worker = new OutboxWorker(store, Map.copyOf(handlerMap), this.properties, clock);
    }

    @Scheduled(fixedDelayString = "${events.outbox.worker-fixed-delay-ms:1000}")
    public void poll() {
        try {
            worker.pollOnce();
        } catch (RuntimeException e) {
            log.warn("[outbox] poll failed: {}", e.toString());
        }
    }
}

