package com.nowcoder.community.search.kafka;

// search-service 幂等消费存储：记录 eventId，避免重复消费；非重复异常必须 fail-closed 触发重试/DLQ。
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SearchConsumedEventStore {

    private final JdbcTemplate jdbcTemplate;

    public SearchConsumedEventStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isConsumed(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            return false;
        }
        try {
            Integer v = jdbcTemplate.queryForObject(
                    "select 1 from search_consumed_event where event_id = ? limit 1",
                    Integer.class,
                    eventId
            );
            return v != null && v == 1;
        } catch (EmptyResultDataAccessException ignored) {
            return false;
        } catch (DataAccessException e) {
            // 幂等表不可用时必须 fail-closed：交给 Kafka 重试/DLQ
            throw e;
        }
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
        } catch (DuplicateKeyException ignored) {
            // 仅唯一约束冲突视为重复消费
            return false;
        } catch (DataAccessException e) {
            // 其余 DB 异常必须显式失败：交给 Kafka 重试/DLQ，避免“已 ack 但索引副作用未落地”的窗口。
            throw e;
        }
    }
}
