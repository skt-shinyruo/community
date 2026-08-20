package com.nowcoder.community.search.application;

import com.nowcoder.community.common.spring.feature.FeatureFlagProperties;
import com.nowcoder.community.search.application.command.SyncPostProjectionCommand;
import com.nowcoder.community.search.domain.model.PostSearchDocument;
import com.nowcoder.community.search.domain.model.PostSearchHit;
import com.nowcoder.community.search.domain.model.PostSearchQuery;
import com.nowcoder.community.search.domain.repository.PostSearchRepository;
import com.nowcoder.community.search.domain.service.PostSearchDomainService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class SearchApplicationService {

    private final PostSearchRepository postSearchRepository;
    private final PostSearchDomainService postSearchDomainService;
    private final SearchPolicyProperties searchPolicyProperties;
    private final FeatureFlagProperties featureFlags;

    public SearchApplicationService(
            PostSearchRepository postSearchRepository,
            PostSearchDomainService postSearchDomainService,
            SearchPolicyProperties searchPolicyProperties,
            FeatureFlagProperties featureFlags
    ) {
        this.postSearchRepository = Objects.requireNonNull(postSearchRepository, "postSearchRepository must not be null");
        this.postSearchDomainService = Objects.requireNonNull(postSearchDomainService, "postSearchDomainService must not be null");
        this.searchPolicyProperties = Objects.requireNonNull(searchPolicyProperties, "searchPolicyProperties must not be null");
        this.featureFlags = Objects.requireNonNull(featureFlags, "featureFlags must not be null");
    }

    public List<SearchPostResult> searchPosts(SearchPostsCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!Boolean.TRUE.equals(featureFlags.getFeatures().getOrDefault("search", true))) {
            return List.of();
        }
        PostSearchQuery query = postSearchDomainService.normalizeSearchQuery(
                command.keyword(),
                command.categoryId(),
                command.tag(),
                command.page(),
                command.size(),
                searchPolicyProperties.getQuery().getMaxPageSize()
        );
        try {
            return postSearchRepository.search(query).stream()
                    .map(this::toResult)
                    .toList();
        } catch (RuntimeException e) {
            if (searchPolicyProperties.getDegradation().isEnabled()) {
                return List.of();
            }
            throw e;
        }
    }

    public void syncPostProjection(SyncPostProjectionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!postSearchDomainService.shouldIndex(command.postId(), command.status())) {
            if (command.postId() != null) {
                postSearchRepository.tombstone(command.postId(), command.aggregateVersion());
            }
            return;
        }
        postSearchRepository.save(toDocument(command));
    }

    public void deletePost(DeleteIndexedPostCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.postId() == null) {
            return;
        }
        postSearchRepository.tombstone(command.postId(), command.aggregateVersion());
    }

    private PostSearchDocument toDocument(SyncPostProjectionCommand command) {
        return new PostSearchDocument(
                command.postId(),
                command.userId(),
                command.categoryId(),
                command.tags(),
                command.title(),
                command.content(),
                command.type(),
                command.status(),
                command.aggregateVersion(),
                command.scoreVersion(),
                command.createTime(),
                command.score()
        );
    }

    private SearchPostResult toResult(PostSearchHit hit) {
        return new SearchPostResult(
                hit.postId(),
                hit.userId(),
                hit.categoryId(),
                hit.tags(),
                hit.title(),
                hit.highlightedTitle(),
                hit.highlightedContent(),
                hit.createTime(),
                hit.score()
        );
    }

    public record SearchPostsCommand(
            String keyword,
            UUID categoryId,
            String tag,
            Integer page,
            Integer size
    ) {
    }

    public record DeleteIndexedPostCommand(UUID postId, long aggregateVersion) {

        public DeleteIndexedPostCommand {
            if (aggregateVersion <= 0L) {
                throw new IllegalArgumentException("post projection aggregateVersion must be positive");
            }
        }
    }

    public record SearchPostResult(
            UUID postId,
            UUID userId,
            UUID categoryId,
            List<String> tags,
            String title,
            String highlightedTitle,
            String highlightedContent,
            Instant createTime,
            Double score
    ) {

        public SearchPostResult {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }
}
