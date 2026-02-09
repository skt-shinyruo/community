package com.nowcoder.community.content.like;

import com.nowcoder.community.social.api.rpc.dto.SocialLikeScanResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 点赞 Redis 投影回填（cold start mitigation）。
 *
 * <p>通过 social-service internal scan 读取 DB SSOT，回填 Redis Set：like:entity:{type}:{id}。</p>
 */
@Component
@ConditionalOnProperty(name = "content.storage", havingValue = "redis", matchIfMissing = true)
public class LikeProjectionBackfillJob {

    private static final Logger log = LoggerFactory.getLogger(LikeProjectionBackfillJob.class);

    private final SocialLikeScanClient scanClient;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    public LikeProjectionBackfillJob(
            SocialLikeScanClient scanClient,
            StringRedisTemplate redisTemplate,
            MeterRegistry meterRegistry
    ) {
        this.scanClient = scanClient;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * @param entityType social_like.entity_type（例如 POST/COMMENT）
     * @param maxItems   最多回填的边数量（防止误触导致长时间运行）
     * @param batchSize  scan 每页大小
     */
    public BackfillResult backfill(int entityType, long maxItems, int batchSize) {
        long max = Math.max(1L, maxItems);
        int bs = Math.min(2000, Math.max(1, batchSize));

        long afterEntityId = 0L;
        long afterUserId = 0L;

        long scanned = 0L;
        long added = 0L;
        int pages = 0;

        while (scanned < max) {
            pages++;
            SocialLikeScanResponse resp = scanClient.scan(entityType, afterEntityId, afterUserId, bs);
            List<SocialLikeScanResponse.SocialLikeScanItem> items = resp == null ? List.of() : resp.getItems();
            if (items == null || items.isEmpty()) {
                break;
            }

            Map<Integer, List<String>> membersByEntityId = new HashMap<>();
            for (SocialLikeScanResponse.SocialLikeScanItem item : items) {
                if (scanned >= max) {
                    break;
                }
                if (item == null) {
                    continue;
                }
                long rawEntityId = item.getEntityId();
                long rawUserId = item.getUserId();
                if (rawEntityId <= 0 || rawUserId <= 0) {
                    continue;
                }
                if (rawEntityId > Integer.MAX_VALUE || rawUserId > Integer.MAX_VALUE) {
                    continue;
                }
                int entityId = (int) rawEntityId;
                int userId = (int) rawUserId;
                membersByEntityId.computeIfAbsent(entityId, k -> new ArrayList<>()).add(String.valueOf(userId));
                scanned++;
            }

            for (Map.Entry<Integer, List<String>> e : membersByEntityId.entrySet()) {
                String key = LikeRedisKeys.entityKey(entityType, e.getKey());
                List<String> members = e.getValue();
                if (members == null || members.isEmpty()) {
                    continue;
                }
                Long n = redisTemplate.opsForSet().add(key, members.toArray(new String[0]));
                if (n != null && n > 0) {
                    added += n;
                }
            }

            SocialLikeScanResponse.SocialLikeScanItem last = items.get(items.size() - 1);
            if (last != null) {
                afterEntityId = Math.max(0L, last.getEntityId());
                afterUserId = Math.max(0L, last.getUserId());
            }

            meterRegistry.counter("content_like_backfill_total", Tags.of("outcome", "page_success")).increment();
            if (resp == null || !resp.isHasMore() || items.size() < bs) {
                break;
            }
        }

        BackfillResult r = new BackfillResult();
        r.setEntityType(entityType);
        r.setScannedItems(scanned);
        r.setAddedMembers(added);
        r.setPages(pages);
        log.info("[like-backfill] entityType={} scanned={} added={} pages={}", entityType, scanned, added, pages);
        return r;
    }

    public static class BackfillResult {
        private int entityType;
        private long scannedItems;
        private long addedMembers;
        private int pages;

        public int getEntityType() {
            return entityType;
        }

        public void setEntityType(int entityType) {
            this.entityType = entityType;
        }

        public long getScannedItems() {
            return scannedItems;
        }

        public void setScannedItems(long scannedItems) {
            this.scannedItems = scannedItems;
        }

        public long getAddedMembers() {
            return addedMembers;
        }

        public void setAddedMembers(long addedMembers) {
            this.addedMembers = addedMembers;
        }

        public int getPages() {
            return pages;
        }

        public void setPages(int pages) {
            this.pages = pages;
        }
    }
}
