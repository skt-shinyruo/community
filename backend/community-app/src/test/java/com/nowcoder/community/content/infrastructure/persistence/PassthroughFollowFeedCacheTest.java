package com.nowcoder.community.content.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.content.application.FollowFeedCache;
import com.nowcoder.community.content.application.FollowFeedCursorCodec;
import com.nowcoder.community.content.application.FollowFeedReadApplicationService;
import com.nowcoder.community.content.application.ContentTextCodec;
import com.nowcoder.community.content.application.PostContentBlockTextProjector;
import com.nowcoder.community.content.application.PostSummaryAssembler;
import com.nowcoder.community.content.application.PostSummaryCache;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import com.nowcoder.community.social.api.query.SocialFollowQueryApi;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PassthroughFollowFeedCacheTest {

    @Test
    void nonRedisContentStorageShouldProvideFollowFeedCacheBeanAndApplicationService() {
        new ApplicationContextRunner()
                .withPropertyValues("content.storage=db")
                .withUserConfiguration(FallbackCacheSelectionTestConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FollowFeedCache.class);
                    assertThat(context).hasSingleBean(FollowFeedReadApplicationService.class);
                    assertThat(context).getBean(FollowFeedCache.class).isInstanceOf(PassthroughFollowFeedCache.class);
                });
    }

    @Test
    void getOrLoadPageShouldPassthroughLoaderAndSanitizeNulls() {
        FollowFeedCache cache = new PassthroughFollowFeedCache();
        UUID first = UUID.randomUUID();
        AtomicInteger loads = new AtomicInteger();

        FollowFeedCache.FollowFeedPageSlice result = cache.getOrLoadPage(UUID.randomUUID(), "", 20, () -> {
            loads.incrementAndGet();
            return new FollowFeedCache.FollowFeedPageSlice(Arrays.asList(first, null, first), null, null);
        });

        assertThat(result.ids()).containsExactly(first);
        assertThat(loads).hasValue(1);
    }

    private static ContentTextCodec textCodec() {
        ContentTextCodec codec = mock(ContentTextCodec.class);
        when(codec.decodeOnRead(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return codec;
    }

    @Configuration
    @Import(PassthroughFollowFeedCache.class)
    static class FallbackCacheSelectionTestConfig {

        @Bean
        FollowFeedReadApplicationService followFeedReadApplicationService(FollowFeedCache followFeedCache) {
            return new FollowFeedReadApplicationService(
                    mock(SocialFollowQueryApi.class),
                    mock(PostContentRepository.class),
                    followFeedCache,
                    mock(CommentContentRepository.class),
                    mock(TagContentRepository.class),
                    mock(PostContentBlockRepository.class),
                    mock(PostSummaryCache.class),
                    mock(PostContentBlockTextProjector.class),
                    new PostSummaryAssembler(textCodec()),
                    new FollowFeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()))
            );
        }
    }
}
