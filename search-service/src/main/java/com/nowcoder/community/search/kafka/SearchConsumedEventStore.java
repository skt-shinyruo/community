package com.nowcoder.community.search.kafka;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SearchConsumedEventStore {

    private final JdbcTemplate jdbcTemplate;

    public SearchConsumedEventStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasConsumed(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            return false;
        }
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "select count(1) from search_consumed_event where event_id = ?",
                    Integer.class,
                    eventId
            );
            return cnt != null && cnt > 0;
        } catch (DataAccessException e) {
            return false;
        }
    }

    public void markConsumed(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            return;
        }
        try {
            jdbcTemplate.update("insert into search_consumed_event(event_id, consumed_at) values(?, now())", eventId);
        } catch (DataAccessException ignored) {
            // duplicate or table missing -> ignore (idempotency only)
        }
    }
}

