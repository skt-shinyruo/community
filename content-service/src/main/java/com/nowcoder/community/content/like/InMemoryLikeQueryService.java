package com.nowcoder.community.content.like;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "content.storage", havingValue = "memory")
public class InMemoryLikeQueryService implements LikeQueryService {

    private final Map<Integer, Set<Integer>> likesByPostId = new ConcurrentHashMap<>();

    @Override
    public long countPostLikes(int postId) {
        Set<Integer> set = likesByPostId.get(postId);
        return set == null ? 0 : set.size();
    }

    @Override
    public boolean hasLikedPost(int userId, int postId) {
        Set<Integer> set = likesByPostId.get(postId);
        return set != null && set.contains(userId);
    }
}

