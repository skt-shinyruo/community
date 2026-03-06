package com.nowcoder.community.social.follow;

import com.nowcoder.community.social.follow.dto.FollowItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * MySQL 持久化实现：以 DB 为 SSOT（source of truth）。
 */
@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "db", matchIfMissing = true)
public class DbFollowRepository implements FollowRepository {

    private final FollowMapper mapper;

    public DbFollowRepository(FollowMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean follow(int userId, int entityType, int entityId, long followTimeMillis) {
        Date createdAt = followTimeMillis > 0 ? new Date(followTimeMillis) : new Date();
        try {
            return mapper.insertFollow(userId, entityType, entityId, createdAt) > 0;
        } catch (DuplicateKeyException ignored) {
            // 幂等：重复关注视为 false
            return false;
        }
    }

    @Override
    public boolean unfollow(int userId, int entityType, int entityId) {
        return mapper.deleteFollow(userId, entityType, entityId) > 0;
    }

    @Override
    public boolean hasFollowed(int userId, int entityType, int entityId) {
        return mapper.countFollow(userId, entityType, entityId) > 0;
    }

    @Override
    public long countFollowees(int userId, int entityType) {
        return mapper.countFollowees(userId, entityType);
    }

    @Override
    public long countFollowers(int entityType, int entityId) {
        return mapper.countFollowers(entityType, entityId);
    }

    @Override
    public List<FollowItem> listFollowees(int userId, int entityType, int offset, int limit) {
        return mapRows(mapper.listFollowees(userId, entityType, offset, limit));
    }

    @Override
    public List<FollowItem> listFollowers(int entityType, int entityId, int offset, int limit) {
        return mapRows(mapper.listFollowers(entityType, entityId, offset, limit));
    }

    private List<FollowItem> mapRows(List<FollowRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<FollowItem> items = new ArrayList<>(rows.size());
        for (FollowRow row : rows) {
            if (row == null) {
                continue;
            }
            FollowItem item = new FollowItem();
            item.setTargetId(row.getTargetId());
            Date t = row.getFollowTime();
            item.setFollowTime(t == null ? null : Instant.ofEpochMilli(t.getTime()));
            items.add(item);
        }
        return items;
    }
}
