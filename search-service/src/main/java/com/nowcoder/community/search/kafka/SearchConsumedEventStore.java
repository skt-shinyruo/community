package com.nowcoder.community.search.kafka;

// search-service 幂等消费存储：以 insert-first 记录 eventId，避免重复索引副作用。
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

    /**
     * @return true 表示首次标记成功，false 表示已存在（重复消费）
     */
    public boolean markConsumedIfFirst(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            return false;
        }
        try {
            jdbcTemplate.update("insert into search_consumed_event(event_id, consumed_at) values(?, now())", eventId);
            return true;
        } catch (DataAccessException ignored) {
            return false;
        }
    }
}
