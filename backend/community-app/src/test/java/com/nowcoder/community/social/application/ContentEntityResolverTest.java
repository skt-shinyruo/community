package com.nowcoder.community.social.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.api.model.ResolvedContentRef;
import com.nowcoder.community.content.api.query.ContentEntityQueryApi;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentEntityResolverTest {

    @Test
    void resolveShouldReturnDirectServiceData() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ContentEntityQueryApi contentEntityQueryApi = mock(ContentEntityQueryApi.class);
        when(contentEntityQueryApi.resolve(1, uuid(100))).thenReturn(new ResolvedContentRef(uuid(2), uuid(100)));

        ContentEntityResolver resolver = new ContentEntityResolver(registry, contentEntityQueryApi);

        ContentEntityResolver.ResolvedEntity resolved = resolver.resolve(1, uuid(100));

        assertThat(resolved.getEntityUserId()).isEqualTo(uuid(2));
        assertThat(resolved.getPostId()).isEqualTo(uuid(100));
        assertThat(registry.find("social_entity_resolve_total")
                .tags("entityType", "1", "source", "service", "outcome", "success")
                .counter()).isNotNull();
    }

    @Test
    void resolveShouldFailClosedWhenResultIsIncomplete() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ContentEntityQueryApi contentEntityQueryApi = mock(ContentEntityQueryApi.class);
        when(contentEntityQueryApi.resolve(1, uuid(100))).thenReturn(new ResolvedContentRef(null, null));

        ContentEntityResolver resolver = new ContentEntityResolver(registry, contentEntityQueryApi);

        assertThatThrownBy(() -> resolver.resolve(1, uuid(100)))
                .isInstanceOf(BusinessException.class);
    }
}
