package com.nowcoder.community.search.application;

import com.nowcoder.community.common.spring.degradation.DegradationDecisions;
import com.nowcoder.community.common.spring.degradation.DegradationProperties;
import com.nowcoder.community.common.spring.feature.FeatureFlagDecisions;
import com.nowcoder.community.common.spring.feature.FeatureFlagProperties;
import com.nowcoder.community.search.application.command.DeleteIndexedPostCommand;
import com.nowcoder.community.search.application.command.SearchPostsCommand;
import com.nowcoder.community.search.application.command.SyncPostProjectionCommand;
import com.nowcoder.community.search.domain.model.PostSearchDocument;
import com.nowcoder.community.search.domain.model.PostSearchHit;
import com.nowcoder.community.search.domain.model.PostSearchQuery;
import com.nowcoder.community.search.domain.repository.PostSearchRepository;
import com.nowcoder.community.search.domain.service.PostSearchDomainService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SearchApplicationServiceTest {

    @Test
    void searchPostsShouldNormalizeQueryAndMapDomainHits() {
        PostSearchRepository repository = mock(PostSearchRepository.class);
        SearchApplicationService service = new SearchApplicationService(repository, new PostSearchDomainService());
        UUID postId = uuid(11);
        UUID categoryId = uuid(3);
        PostSearchQuery expectedQuery = new PostSearchQuery("spring", categoryId, "java", 0, 50);
        when(repository.search(expectedQuery)).thenReturn(List.of(new PostSearchHit(
                postId,
                uuid(7),
                categoryId,
                List.of("java"),
                "spring",
                "<em>spring</em>",
                null,
                Instant.parse("2026-03-28T00:00:00Z"),
                10.0
        )));

        var results = service.searchPosts(new SearchPostsCommand(" spring ", categoryId, "#java", -1, 100));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).postId()).isEqualTo(postId);
        assertThat(results.get(0).highlightedTitle()).isEqualTo("<em>spring</em>");
        verify(repository).search(expectedQuery);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void searchPostsShouldUseConfiguredMaxPageSize() {
        PostSearchRepository repository = mock(PostSearchRepository.class);
        SearchPolicyProperties properties = new SearchPolicyProperties();
        properties.getQuery().setMaxPageSize(20);
        SearchApplicationService service = new SearchApplicationService(
                repository,
                new PostSearchDomainService(),
                properties
        );
        PostSearchQuery expectedQuery = new PostSearchQuery("spring", null, null, 0, 20);
        when(repository.search(expectedQuery)).thenReturn(List.of());

        var results = service.searchPosts(new SearchPostsCommand("spring", null, null, 0, 100));

        assertThat(results).isEmpty();
        verify(repository).search(expectedQuery);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void searchPostsShouldReturnEmptyResultsWhenDegradationIsEnabledAndRepositoryFails() {
        PostSearchRepository repository = mock(PostSearchRepository.class);
        SearchPolicyProperties properties = new SearchPolicyProperties();
        properties.getDegradation().setEnabled(true);
        SearchApplicationService service = new SearchApplicationService(
                repository,
                new PostSearchDomainService(),
                properties
        );
        when(repository.search(new PostSearchQuery("spring", null, null, 0, 10)))
                .thenThrow(new IllegalStateException("es unavailable"));

        var results = service.searchPosts(new SearchPostsCommand("spring", null, null, 0, 10));

        assertThat(results).isEmpty();
    }

    @Test
    void searchPostsShouldReturnEmptyResultsWhenNacosSearchDegradationIsBestEffort() {
        PostSearchRepository repository = mock(PostSearchRepository.class);
        DegradationProperties degradationProperties = new DegradationProperties();
        degradationProperties.getModes().put("search", "best-effort");
        SearchApplicationService service = new SearchApplicationService(
                repository,
                new PostSearchDomainService(),
                new SearchPolicyProperties(),
                new FeatureFlagDecisions(new FeatureFlagProperties()),
                new DegradationDecisions(degradationProperties)
        );
        when(repository.search(new PostSearchQuery("spring", null, null, 0, 10)))
                .thenThrow(new IllegalStateException("es unavailable"));

        var results = service.searchPosts(new SearchPostsCommand("spring", null, null, 0, 10));

        assertThat(results).isEmpty();
    }

    @Test
    void searchPostsShouldSkipRepositoryWhenNacosFeatureFlagDisablesSearch() {
        PostSearchRepository repository = mock(PostSearchRepository.class);
        FeatureFlagProperties featureFlagProperties = new FeatureFlagProperties();
        featureFlagProperties.getFlags().put("search", false);
        SearchApplicationService service = new SearchApplicationService(
                repository,
                new PostSearchDomainService(),
                new SearchPolicyProperties(),
                new FeatureFlagDecisions(featureFlagProperties),
                new DegradationDecisions(new DegradationProperties())
        );

        var results = service.searchPosts(new SearchPostsCommand("spring", null, null, 0, 10));

        assertThat(results).isEmpty();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void searchPostsShouldPropagateRepositoryFailureWhenDegradationIsDisabled() {
        PostSearchRepository repository = mock(PostSearchRepository.class);
        SearchApplicationService service = new SearchApplicationService(repository, new PostSearchDomainService());
        when(repository.search(new PostSearchQuery("spring", null, null, 0, 10)))
                .thenThrow(new IllegalStateException("es unavailable"));

        assertThatThrownBy(() -> service.searchPosts(new SearchPostsCommand("spring", null, null, 0, 10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("es unavailable");
    }

    @Test
    void syncPostProjectionShouldSaveActiveProjectionAsDomainDocument() {
        PostSearchRepository repository = mock(PostSearchRepository.class);
        SearchApplicationService service = new SearchApplicationService(repository, new PostSearchDomainService());
        UUID postId = uuid(11);

        service.syncPostProjection(new SyncPostProjectionCommand(
                postId,
                uuid(7),
                uuid(3),
                List.of("java"),
                "title",
                "content",
                0,
                0,
                Instant.parse("2026-03-28T00:00:00Z"),
                1.5
        ));

        ArgumentCaptor<PostSearchDocument> documentCaptor = ArgumentCaptor.forClass(PostSearchDocument.class);
        verify(repository).save(documentCaptor.capture());
        assertThat(documentCaptor.getValue().postId()).isEqualTo(postId);
        assertThat(documentCaptor.getValue().tags()).containsExactly("java");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void syncPostProjectionShouldDeleteDeletedProjection() {
        PostSearchRepository repository = mock(PostSearchRepository.class);
        SearchApplicationService service = new SearchApplicationService(repository, new PostSearchDomainService());
        UUID postId = uuid(11);

        service.syncPostProjection(new SyncPostProjectionCommand(
                postId,
                uuid(7),
                uuid(3),
                List.of("java"),
                "title",
                "content",
                0,
                2,
                Instant.parse("2026-03-28T00:00:00Z"),
                1.5
        ));

        verify(repository).delete(postId);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void deletePostShouldIgnoreNullIds() {
        PostSearchRepository repository = mock(PostSearchRepository.class);
        SearchApplicationService service = new SearchApplicationService(repository, new PostSearchDomainService());

        service.deletePost(new DeleteIndexedPostCommand(null));

        verifyNoMoreInteractions(repository);
    }
}
