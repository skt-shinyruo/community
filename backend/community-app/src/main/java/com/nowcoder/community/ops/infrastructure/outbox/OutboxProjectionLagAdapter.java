package com.nowcoder.community.ops.infrastructure.outbox;

import com.nowcoder.community.ops.application.ProjectionLagPort;
import com.nowcoder.community.ops.application.result.ProjectionLagResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class OutboxProjectionLagAdapter implements ProjectionLagPort {

    private final JdbcTemplate jdbcTemplate;
    private final SpringOutboxHandlerCatalog outboxHandlerCatalog;

    public OutboxProjectionLagAdapter(JdbcTemplate jdbcTemplate, SpringOutboxHandlerCatalog outboxHandlerCatalog) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.outboxHandlerCatalog = Objects.requireNonNull(outboxHandlerCatalog, "outboxHandlerCatalog must not be null");
    }

    @Override
    public List<ProjectionLagResult> listProjectionLag() {
        List<String> trackedTopics = trackedProjectionTopics();
        if (trackedTopics.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        String placeholders = trackedTopics.stream().map(topic -> "?").collect(Collectors.joining(", "));
        return jdbcTemplate.query(
                """
                        select topic, status, count(*) as row_count, min(created_at) as oldest_created_at
                        from outbox_event
                        where topic in (%s)
                          and status in ('PENDING', 'PROCESSING', 'DEAD')
                        group by topic, status
                        order by topic asc, status asc
                        """.formatted(placeholders),
                (rs, rowNum) -> {
                    Timestamp oldest = rs.getTimestamp("oldest_created_at");
                    Duration age = oldest == null ? Duration.ZERO : Duration.between(oldest.toInstant(), now);
                    return new ProjectionLagResult(
                            rs.getString("topic"),
                            rs.getString("status"),
                            rs.getLong("row_count"),
                            age.isNegative() ? Duration.ZERO : age
                    );
                },
                trackedTopics.toArray()
        );
    }

    private List<String> trackedProjectionTopics() {
        Set<String> topics = outboxHandlerCatalog.topics();
        if (topics == null || topics.isEmpty()) {
            return List.of();
        }
        return topics.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(this::isProjectionTopic)
                .sorted()
                .toList();
    }

    private boolean isProjectionTopic(String topic) {
        return topic.contains("projection");
    }
}
