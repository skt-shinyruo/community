package com.nowcoder.community.search.infrastructure.persistence;

import com.nowcoder.community.search.application.SearchIndexRebuildPort;
import com.nowcoder.community.search.domain.model.PostSearchDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElasticsearchSearchIndexRebuildAdapterTest {

    private static final String TARGET_INDEX = "community_posts_v20260803010101";
    private static final Duration VISIBILITY_TTL = Duration.ofSeconds(30);

    @Test
    void beginShouldPreserveTheIndexWhenTargetActivationOutcomeIsUnknown() {
        Fixture fixture = fixture();
        RuntimeException activationFailure = new IllegalStateException("SET response lost");
        when(fixture.targetRegistry.activate(TARGET_INDEX, VISIBILITY_TTL)).thenThrow(activationFailure);

        assertThatThrownBy(() -> fixture.adapter.begin(VISIBILITY_TTL))
                .isSameAs(activationFailure);

        verify(fixture.indexManager, never()).deleteIndex(TARGET_INDEX);
        verify(fixture.targetRenewer, never()).scheduleAtFixedRate(
                any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)
        );
    }

    @Test
    void closeShouldPreserveAnUnpublishedIndexWhenTargetRemovalOutcomeIsUnknown() {
        Fixture fixture = fixture();
        doReturn(fixture.renewal).when(fixture.targetRenewer).scheduleAtFixedRate(
                any(Runnable.class), eq(10_000L), eq(10_000L), eq(TimeUnit.MILLISECONDS)
        );
        doThrow(new IllegalStateException("DEL response lost"))
                .when(fixture.targetRegistry).deactivate(TARGET_INDEX);
        SearchIndexRebuildPort.RebuildSession session = fixture.adapter.begin(VISIBILITY_TTL);

        assertThatCode(session::close).doesNotThrowAnyException();

        verify(fixture.renewal).cancel(false);
        verify(fixture.indexManager, never()).deleteIndex(TARGET_INDEX);
    }

    @Test
    void schedulingFailureShouldRemainPrimaryAndPreserveIndexWhenRemovalIsUnknown() {
        Fixture fixture = fixture();
        RuntimeException schedulingFailure = new IllegalStateException("scheduler rejected task");
        RuntimeException removalFailure = new IllegalStateException("DEL response lost");
        doThrow(schedulingFailure).when(fixture.targetRenewer).scheduleAtFixedRate(
                any(Runnable.class), eq(10_000L), eq(10_000L), eq(TimeUnit.MILLISECONDS)
        );
        doThrow(removalFailure).when(fixture.targetRegistry).deactivate(TARGET_INDEX);

        Throwable thrown = catchThrowable(() -> fixture.adapter.begin(VISIBILITY_TTL));

        assertThat(thrown).isSameAs(schedulingFailure);
        assertThat(thrown.getSuppressed()).containsExactly(removalFailure);
        verify(fixture.indexManager, never()).deleteIndex(TARGET_INDEX);
    }

    @Test
    void rebuildTargetShouldRenewOnAHeartbeatInsteadOfOncePerDocument() {
        Fixture fixture = fixture();
        ArgumentCaptor<Runnable> heartbeatCaptor = ArgumentCaptor.forClass(Runnable.class);
        doReturn(fixture.renewal).when(fixture.targetRenewer).scheduleAtFixedRate(
                heartbeatCaptor.capture(), eq(10_000L), eq(10_000L), eq(TimeUnit.MILLISECONDS)
        );
        when(fixture.targetRegistry.refresh(TARGET_INDEX, VISIBILITY_TTL)).thenReturn(true);

        SearchIndexRebuildPort.RebuildSession session = fixture.adapter.begin(VISIBILITY_TTL);
        session.appendPage(List.of(document()));

        verify(fixture.repository).saveAllToIndex(List.of(document()), TARGET_INDEX);
        verify(fixture.targetRegistry, never()).refresh(TARGET_INDEX, VISIBILITY_TTL);

        heartbeatCaptor.getValue().run();

        verify(fixture.targetRegistry).refresh(TARGET_INDEX, VISIBILITY_TTL);
        assertThat(session.isValid()).isTrue();
        session.close();
        verify(fixture.renewal).cancel(false);
        verify(fixture.targetRegistry).deactivate(TARGET_INDEX);
        verify(fixture.indexManager).deleteIndex(TARGET_INDEX);
    }

    @Test
    void rebuildTargetShouldBecomeInvalidWhenHeartbeatLosesOwnership() {
        Fixture fixture = fixture();
        ArgumentCaptor<Runnable> heartbeatCaptor = ArgumentCaptor.forClass(Runnable.class);
        doReturn(fixture.renewal).when(fixture.targetRenewer).scheduleAtFixedRate(
                heartbeatCaptor.capture(), eq(10_000L), eq(10_000L), eq(TimeUnit.MILLISECONDS)
        );
        when(fixture.targetRegistry.refresh(TARGET_INDEX, VISIBILITY_TTL)).thenReturn(false);

        SearchIndexRebuildPort.RebuildSession session = fixture.adapter.begin(VISIBILITY_TTL);
        heartbeatCaptor.getValue().run();

        assertThat(session.isValid()).isFalse();
        assertThatThrownBy(() -> session.appendPage(List.of(document())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid");
        session.close();
        verify(fixture.indexManager).deleteIndex(TARGET_INDEX);
    }

    @Test
    void publishShouldPreserveTheTargetWhenAliasOutcomeIsAmbiguous() {
        Fixture fixture = fixture();
        doReturn(fixture.renewal).when(fixture.targetRenewer).scheduleAtFixedRate(
                any(Runnable.class), eq(10_000L), eq(10_000L), eq(TimeUnit.MILLISECONDS)
        );
        when(fixture.targetRegistry.refresh(TARGET_INDEX, VISIBILITY_TTL)).thenReturn(true);
        doThrow(new IllegalStateException("alias response lost"))
                .when(fixture.indexManager).switchAliasTo(TARGET_INDEX);

        SearchIndexRebuildPort.RebuildSession session = fixture.adapter.begin(VISIBILITY_TTL);

        assertThatThrownBy(session::publish)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("alias response lost");
        session.close();

        verify(fixture.indexManager, never()).deleteIndex(TARGET_INDEX);
        verify(fixture.targetRegistry).deactivate(TARGET_INDEX);
    }

    private static Fixture fixture() {
        PostIndexManager indexManager = mock(PostIndexManager.class);
        ElasticsearchPostSearchRepository repository = mock(ElasticsearchPostSearchRepository.class);
        SearchReindexTargetRegistry targetRegistry = mock(SearchReindexTargetRegistry.class);
        ScheduledExecutorService targetRenewer = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> renewal = mock(ScheduledFuture.class);
        when(indexManager.createNewIndex()).thenReturn(TARGET_INDEX);
        when(targetRegistry.activate(TARGET_INDEX, VISIBILITY_TTL)).thenReturn(true);
        ElasticsearchSearchIndexRebuildAdapter adapter = new ElasticsearchSearchIndexRebuildAdapter(
                indexManager, repository, targetRegistry, targetRenewer, false
        );
        return new Fixture(indexManager, repository, targetRegistry, targetRenewer, renewal, adapter);
    }

    private static PostSearchDocument document() {
        return new PostSearchDocument(
                uuid(101),
                uuid(7),
                uuid(3),
                List.of("java"),
                "title",
                "content",
                0,
                0,
                7L,
                3L,
                Instant.parse("2026-03-28T00:00:00Z"),
                1.5
        );
    }

    private record Fixture(
            PostIndexManager indexManager,
            ElasticsearchPostSearchRepository repository,
            SearchReindexTargetRegistry targetRegistry,
            ScheduledExecutorService targetRenewer,
            ScheduledFuture<?> renewal,
            ElasticsearchSearchIndexRebuildAdapter adapter
    ) {
    }
}
