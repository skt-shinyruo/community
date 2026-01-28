package com.nowcoder.community.message.kafka;

// message-service 幂等表清理任务：按 retention-days 删除过期记录。
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@ConditionalOnProperty(name = "message.idempotency.cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class ConsumedEventCleanupJob {

    private final JdbcTemplate jdbcTemplate;
    private final int retentionDays;

    public ConsumedEventCleanupJob(
            JdbcTemplate jdbcTemplate,
            @Value("${message.idempotency.retention-days:7}") int retentionDays
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Scheduled(fixedDelayString = "${message.idempotency.cleanup-interval-ms:21600000}")
    public void cleanup() {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
        jdbcTemplate.update("delete from consumed_event where consumed_at < ?", cutoff);
    }
}

