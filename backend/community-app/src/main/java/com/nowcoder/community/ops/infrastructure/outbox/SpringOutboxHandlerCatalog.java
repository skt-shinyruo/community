package com.nowcoder.community.ops.infrastructure.outbox;

import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.ops.application.OutboxHandlerCatalog;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SpringOutboxHandlerCatalog implements OutboxHandlerCatalog {

    private final ObjectProvider<List<OutboxHandler>> handlersProvider;

    public SpringOutboxHandlerCatalog(ObjectProvider<List<OutboxHandler>> handlersProvider) {
        this.handlersProvider = handlersProvider;
    }

    @Override
    public boolean hasHandler(String topic) {
        return StringUtils.hasText(topic) && topics().contains(topic.trim());
    }

    @Override
    public Set<String> topics() {
        List<OutboxHandler> handlers = handlersProvider == null ? null : handlersProvider.getIfAvailable();
        if (handlers == null || handlers.isEmpty()) {
            return Set.of();
        }
        return handlers.stream()
                .filter(handler -> handler != null && StringUtils.hasText(handler.topic()))
                .map(handler -> handler.topic().trim())
                .collect(Collectors.toUnmodifiableSet());
    }
}
