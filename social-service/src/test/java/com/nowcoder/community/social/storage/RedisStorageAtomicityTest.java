package com.nowcoder.community.social.storage;

import com.nowcoder.community.common.internal.dto.EntityResolveResponse;
import com.nowcoder.community.social.event.SocialEventPublisher;
import com.nowcoder.community.social.follow.FollowService;
import com.nowcoder.community.social.follow.RedisFollowRepository;
import com.nowcoder.community.social.follow.dto.FollowRequest;
import com.nowcoder.community.social.like.LikeService;
import com.nowcoder.community.social.like.RedisLikeRepository;
import com.nowcoder.community.social.like.dto.LikeRequest;
import com.nowcoder.community.social.service.ContentServiceClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis 存储模式（social.storage=redis）的关键一致性保障回归：
 * - follow：双 ZSet 原子双写（避免并发重复 created=true / 双写不一致）
 * - like：关系 Set + 用户获赞计数原子更新（避免中途失败导致计数漂移）
 *
 * <p>该用例依赖 Docker（Testcontainers），无 Docker 环境自动跳过。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisStorageAtomicityTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void setupRedisClient() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void closeRedisClient() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void flushRedis() {
        flushAll();
    }

    private void flushAll() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    void followShouldCreateOnceUnderConcurrencyAndKeepTwoSidesConsistent()
            throws BrokenBarrierException, InterruptedException, TimeoutException, java.util.concurrent.ExecutionException {
        RedisFollowRepository repo = new RedisFollowRepository(redisTemplate);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 100; i++) {
                flushAll();

                CyclicBarrier barrier = new CyclicBarrier(2);
                long now = System.currentTimeMillis();

                Future<Boolean> f1 = executor.submit(() -> {
                    barrier.await(3, TimeUnit.SECONDS);
                    return repo.follow(1, 3, 2, now);
                });
                Future<Boolean> f2 = executor.submit(() -> {
                    barrier.await(3, TimeUnit.SECONDS);
                    return repo.follow(1, 3, 2, now);
                });

                boolean r1 = f1.get(3, TimeUnit.SECONDS);
                boolean r2 = f2.get(3, TimeUnit.SECONDS);

                int createdCount = (r1 ? 1 : 0) + (r2 ? 1 : 0);
                assertThat(createdCount).isEqualTo(1);
                assertThat(repo.hasFollowed(1, 3, 2)).isTrue();
                assertThat(repo.countFollowees(1, 3)).isEqualTo(1);
                assertThat(repo.countFollowers(3, 2)).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void followShouldHealWhenOnlyFolloweeSideExists() {
        RedisFollowRepository repo = new RedisFollowRepository(redisTemplate);

        redisTemplate.opsForZSet().add("followee:1:3", "2", 123);

        boolean created = repo.follow(1, 3, 2, 999);
        assertThat(created).isFalse();

        Double followerScore = redisTemplate.opsForZSet().score("follower:3:2", "1");
        assertThat(followerScore).isNotNull();
        assertThat(followerScore.longValue()).isEqualTo(123L);
    }

    @Test
    void followServiceShouldRollbackStateWhenPublishFailsInRedisMode() {
        RedisFollowRepository repo = new RedisFollowRepository(redisTemplate);
        SocialEventPublisher publisher = mock(SocialEventPublisher.class);
        doThrow(new RuntimeException("boom")).when(publisher).publishFollowCreated(any());

        FollowService service = new FollowService(repo, publisher, null, "redis");
        FollowRequest request = new FollowRequest();
        request.setEntityType(3);
        request.setEntityId(2);

        assertThatThrownBy(() -> service.follow(1, request)).isInstanceOf(RuntimeException.class);

        assertThat(repo.hasFollowed(1, 3, 2)).isFalse();
        assertThat(repo.countFollowees(1, 3)).isEqualTo(0);
        assertThat(repo.countFollowers(3, 2)).isEqualTo(0);
    }

    @Test
    void likeRepositorySetLikeShouldUpdateRelationAndCounterAtomicallyAndIdempotently() {
        RedisLikeRepository repo = new RedisLikeRepository(redisTemplate);

        assertThat(repo.setLike(1, 1, 100, 2, true)).isTrue();
        assertThat(repo.isLiked(1, 1, 100)).isTrue();
        assertThat(repo.countEntityLikes(1, 100)).isEqualTo(1);
        assertThat(repo.getUserLikeCount(2)).isEqualTo(1);

        assertThat(repo.setLike(1, 1, 100, 2, true)).isFalse();
        assertThat(repo.countEntityLikes(1, 100)).isEqualTo(1);
        assertThat(repo.getUserLikeCount(2)).isEqualTo(1);

        assertThat(repo.setLike(1, 1, 100, 2, false)).isTrue();
        assertThat(repo.isLiked(1, 1, 100)).isFalse();
        assertThat(repo.countEntityLikes(1, 100)).isEqualTo(0);
        assertThat(repo.getUserLikeCount(2)).isEqualTo(0);

        assertThat(repo.setLike(1, 1, 100, 2, false)).isFalse();
        assertThat(repo.getUserLikeCount(2)).isEqualTo(0);
    }

    @Test
    void likeServiceShouldRollbackStateWhenPublishFailsInRedisMode() {
        RedisLikeRepository repo = new RedisLikeRepository(redisTemplate);

        SocialEventPublisher publisher = mock(SocialEventPublisher.class);
        doThrow(new RuntimeException("boom")).when(publisher).publishLikeCreated(any());

        ContentServiceClient contentServiceClient = mock(ContentServiceClient.class);
        EntityResolveResponse resolved = new EntityResolveResponse();
        resolved.setEntityType(1);
        resolved.setEntityId(100);
        resolved.setEntityUserId(2);
        resolved.setPostId(100);
        when(contentServiceClient.resolveEntity(1, 100)).thenReturn(resolved);

        LikeService service = new LikeService(repo, publisher, contentServiceClient, null, "redis");

        LikeRequest request = new LikeRequest();
        request.setEntityType(1);
        request.setEntityId(100);
        request.setLiked(true);

        assertThatThrownBy(() -> service.setLike(1, request)).isInstanceOf(RuntimeException.class);

        assertThat(repo.isLiked(1, 1, 100)).isFalse();
        assertThat(repo.countEntityLikes(1, 100)).isEqualTo(0);
        assertThat(repo.getUserLikeCount(2)).isEqualTo(0);
    }
}
