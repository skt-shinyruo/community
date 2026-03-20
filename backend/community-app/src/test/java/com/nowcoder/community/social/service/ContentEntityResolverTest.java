package com.nowcoder.community.social.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.service.ContentEntityService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentEntityResolverTest {

    @Test
    void resolveShouldReturnDirectServiceData() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ContentEntityService contentEntityService = mock(ContentEntityService.class);
        when(contentEntityService.resolve(1, 100)).thenReturn(new ContentEntityService.ResolvedEntity(2, 100));

        ContentEntityResolver resolver = new ContentEntityResolver(registry, contentEntityService);

        ContentEntityResolver.ResolvedEntity resolved = resolver.resolve(1, 100);

        assertThat(resolved.getEntityUserId()).isEqualTo(2);
        assertThat(resolved.getPostId()).isEqualTo(100);
        assertThat(registry.find("social_entity_resolve_total")
                .tags("entityType", "1", "source", "service", "outcome", "success")
                .counter()).isNotNull();
    }

    @Test
    void resolveShouldFailClosedWhenResultIsIncomplete() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ContentEntityService contentEntityService = mock(ContentEntityService.class);
        when(contentEntityService.resolve(1, 100)).thenReturn(new ContentEntityService.ResolvedEntity(0, 0));

        ContentEntityResolver resolver = new ContentEntityResolver(registry, contentEntityService);

        assertThatThrownBy(() -> resolver.resolve(1, 100))
                .isInstanceOf(BusinessException.class);
    }
}
