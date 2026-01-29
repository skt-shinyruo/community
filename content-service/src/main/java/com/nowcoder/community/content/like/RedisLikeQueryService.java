package com.nowcoder.community.content.like;

import com.nowcoder.community.common.domain.EntityTypes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "content.storage", havingValue = "redis", matchIfMissing = true)
public class RedisLikeQueryService implements LikeQueryService {

    private static final int ENTITY_TYPE_POST = EntityTypes.POST;

    private final StringRedisTemplate redisTemplate;

    public RedisLikeQueryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long countPostLikes(int postId) {
        Long size = redisTemplate.opsForSet().size(entityKey(ENTITY_TYPE_POST, postId));
        return size == null ? 0 : size;
    }

    @Override
    public boolean hasLikedPost(int userId, int postId) {
        if (userId <= 0) {
            return false;
        }
        Boolean member = redisTemplate.opsForSet().isMember(entityKey(ENTITY_TYPE_POST, postId), String.valueOf(userId));
        return member != null && member;
    }

    private String entityKey(int entityType, int entityId) {
        return "like:entity:" + entityType + ":" + entityId;
    }
}
