package com.nowcoder.community.social.block;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisBlockRepositoryTest {

    @Test
    void scanBlocksAfterShouldUseNonBlockingScanInsteadOfKeys() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        RedisConnection connection = mock(RedisConnection.class);
        @SuppressWarnings("unchecked")
        Cursor<byte[]> cursor = mock(Cursor.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<?> callback = invocation.getArgument(0);
            return callback.doInRedis(connection);
        });
        when(connection.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, true, false);
        when(cursor.next()).thenReturn(
                bytes("block:" + uuid(2)),
                bytes("skip-me"),
                bytes("block:" + uuid(1))
        );
        when(setOperations.members("block:" + uuid(1)))
                .thenReturn(Set.of(uuid(3).toString(), uuid(2).toString()));
        when(setOperations.members("block:" + uuid(2)))
                .thenReturn(Set.of(uuid(4).toString()));

        RedisBlockRepository repository = new RedisBlockRepository(redisTemplate);

        List<BlockScanRow> rows = repository.scanBlocksAfter(uuid(1), uuid(2), 2);

        assertThat(rows)
                .extracting(BlockScanRow::getUserId, BlockScanRow::getTargetUserId)
                .containsExactly(
                        tuple(uuid(1), uuid(3)),
                        tuple(uuid(2), uuid(4))
                );
        verify(redisTemplate, never()).keys(anyString());
        verify(connection).scan(any(ScanOptions.class));
        verify(cursor).close();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
