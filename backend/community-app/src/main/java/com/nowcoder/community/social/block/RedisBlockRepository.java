// Redis 拉黑关系实现：key=block:{userId}，value=set(targetUserId)。
package com.nowcoder.community.social.block;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "redis")
public class RedisBlockRepository implements BlockRepository {

    private static final String BLOCK_KEY_PATTERN = "block:*";
    private static final int BLOCK_KEY_SCAN_COUNT = 512;

    private final StringRedisTemplate redisTemplate;

    public RedisBlockRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean block(UUID userId, UUID targetUserId) {
        Long added = redisTemplate.opsForSet().add(key(userId), String.valueOf(targetUserId));
        return added != null && added > 0;
    }

    @Override
    public boolean unblock(UUID userId, UUID targetUserId) {
        Long removed = redisTemplate.opsForSet().remove(key(userId), String.valueOf(targetUserId));
        return removed != null && removed > 0;
    }

    @Override
    public boolean hasBlocked(UUID userId, UUID targetUserId) {
        Boolean member = redisTemplate.opsForSet().isMember(key(userId), String.valueOf(targetUserId));
        return Boolean.TRUE.equals(member);
    }

    @Override
    public List<UUID> listBlockedUserIds(UUID userId) {
        Set<String> members = redisTemplate.opsForSet().members(key(userId));
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        List<UUID> list = new ArrayList<>(members.size());
        for (String s : members) {
            if (s == null) continue;
            try {
                list.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return list;
    }

    @Override
    public List<BlockScanRow> scanBlocksAfter(UUID afterUserId, UUID afterTargetUserId, int limit) {
        int normalizedLimit = Math.min(500, Math.max(1, limit));
        UUID normalizedAfterUserId = afterUserId == null ? new UUID(0L, 0L) : afterUserId;
        UUID normalizedAfterTargetUserId = afterTargetUserId == null ? new UUID(0L, 0L) : afterTargetUserId;
        List<UUID> blockerIds = scanBlockerIds();
        if (blockerIds.isEmpty()) {
            return List.of();
        }

        List<BlockScanRow> rows = new ArrayList<>();
        for (UUID blockerId : blockerIds) {
            List<UUID> blockedIds = listBlockedUserIds(blockerId).stream()
                    .sorted(Comparator.naturalOrder())
                    .toList();
            for (UUID blockedId : blockedIds) {
                if (blockerId.compareTo(normalizedAfterUserId) < 0) {
                    continue;
                }
                if (blockerId.equals(normalizedAfterUserId) && blockedId.compareTo(normalizedAfterTargetUserId) <= 0) {
                    continue;
                }
                BlockScanRow row = new BlockScanRow();
                row.setUserId(blockerId);
                row.setTargetUserId(blockedId);
                rows.add(row);
                if (rows.size() >= normalizedLimit) {
                    return rows;
                }
            }
        }
        return rows;
    }

    private String key(UUID userId) {
        return "block:" + userId;
    }

    private List<UUID> scanBlockerIds() {
        List<UUID> blockerIds = redisTemplate.execute((RedisCallback<List<UUID>>) connection -> {
            if (connection == null) {
                return List.<UUID>of();
            }
            ScanOptions options = ScanOptions.scanOptions()
                    .match(BLOCK_KEY_PATTERN)
                    .count(BLOCK_KEY_SCAN_COUNT)
                    .build();
            List<UUID> scanned = new ArrayList<>();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    UUID blockerId = parseUserIdFromKey(cursor.next());
                    if (blockerId != null) {
                        scanned.add(blockerId);
                    }
                }
            }
            return scanned;
        });
        if (blockerIds == null || blockerIds.isEmpty()) {
            return List.of();
        }
        return blockerIds.stream()
                .sorted()
                .toList();
    }

    private UUID parseUserIdFromKey(byte[] rawKey) {
        if (rawKey == null) {
            return null;
        }
        return parseUserIdFromKey(new String(rawKey, StandardCharsets.UTF_8));
    }

    private UUID parseUserIdFromKey(String key) {
        if (key == null || !key.startsWith("block:")) {
            return null;
        }
        try {
            return UUID.fromString(key.substring("block:".length()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
