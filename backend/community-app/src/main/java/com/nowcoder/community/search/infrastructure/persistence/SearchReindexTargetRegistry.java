package com.nowcoder.community.search.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

/**
 * Shares the active rebuild target across application instances so live projections can dual-write it.
 */
@Component
public class SearchReindexTargetRegistry {

    private static final Logger log = LoggerFactory.getLogger(SearchReindexTargetRegistry.class);
    private static final String ACTIVE_TARGET_KEY = "search:reindex:active-target";
    private static final DefaultRedisScript<Long> REFRESH_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> REMOVE_SCRIPT = new DefaultRedisScript<>();

    static {
        REFRESH_SCRIPT.setResultType(Long.class);
        REFRESH_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then "
                        + "return redis.call('pexpire', KEYS[1], ARGV[2]) "
                        + "else return 0 end"
        );
        REMOVE_SCRIPT.setResultType(Long.class);
        REMOVE_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then "
                        + "return redis.call('del', KEYS[1]) "
                        + "else return 0 end"
        );
    }

    private final StringRedisTemplate redisTemplate;

    public SearchReindexTargetRegistry(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * @return {@code false} only when this call cannot have registered the target: the input is invalid,
     * or Redis confirmed that another target already owns the key
     * @throws IllegalStateException when the command outcome cannot be determined
     */
    public boolean activate(String indexName, Duration ttl) {
        if (!valid(indexName, ttl)) {
            return false;
        }
        Boolean activated;
        try {
            activated = redisTemplate.opsForValue().setIfAbsent(ACTIVE_TARGET_KEY, indexName, ttl);
        } catch (RuntimeException failure) {
            log.warn("[search-reindex] rebuild target registration outcome is unknown", failure);
            throw new IllegalStateException("search reindex target registration outcome is unknown", failure);
        }
        if (activated == null) {
            throw new IllegalStateException("search reindex target registration outcome is unknown");
        }
        return activated;
    }

    public Optional<String> currentIndex() {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(ACTIVE_TARGET_KEY))
                    .filter(StringUtils::hasText);
        } catch (RuntimeException failure) {
            log.warn("[search-reindex] failed to resolve rebuild target", failure);
            throw new IllegalStateException("search reindex target registry is unavailable", failure);
        }
    }

    public boolean refresh(String indexName, Duration ttl) {
        if (!valid(indexName, ttl)) {
            return false;
        }
        try {
            Long refreshed = redisTemplate.execute(
                    REFRESH_SCRIPT,
                    Collections.singletonList(ACTIVE_TARGET_KEY),
                    indexName,
                    Long.toString(ttl.toMillis())
            );
            return refreshed != null && refreshed > 0L;
        } catch (RuntimeException failure) {
            log.warn("[search-reindex] failed to refresh rebuild target", failure);
            return false;
        }
    }

    /**
     * Returns only after Redis confirms that the key no longer belongs to {@code indexName}.
     * An uncertain response must be surfaced so callers do not delete a possibly referenced index.
     */
    public void deactivate(String indexName) {
        if (!StringUtils.hasText(indexName)) {
            return;
        }
        Long removed;
        try {
            removed = redisTemplate.execute(
                    REMOVE_SCRIPT,
                    Collections.singletonList(ACTIVE_TARGET_KEY),
                    indexName
            );
        } catch (RuntimeException failure) {
            log.warn("[search-reindex] rebuild target removal outcome is unknown", failure);
            throw new IllegalStateException("search reindex target removal outcome is unknown", failure);
        }
        if (removed == null || (removed != 0L && removed != 1L)) {
            throw new IllegalStateException("search reindex target removal outcome is unknown");
        }
    }

    private boolean valid(String indexName, Duration ttl) {
        return StringUtils.hasText(indexName) && ttl != null && !ttl.isZero() && !ttl.isNegative();
    }
}
