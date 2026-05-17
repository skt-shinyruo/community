package com.nowcoder.community.search.application;

import com.nowcoder.community.common.spring.degradation.DegradationDecisions;
import com.nowcoder.community.common.spring.degradation.DegradationProperties;
import com.nowcoder.community.common.spring.feature.FeatureFlagDecisions;
import com.nowcoder.community.common.spring.feature.FeatureFlagProperties;
import com.nowcoder.community.search.application.command.DeleteIndexedPostCommand;
import com.nowcoder.community.search.application.command.SearchPostsCommand;
import com.nowcoder.community.search.application.command.SyncPostProjectionCommand;
import com.nowcoder.community.search.application.result.SearchPostResult;
import com.nowcoder.community.search.domain.model.PostSearchDocument;
import com.nowcoder.community.search.domain.model.PostSearchHit;
import com.nowcoder.community.search.domain.model.PostSearchQuery;
import com.nowcoder.community.search.domain.repository.PostSearchRepository;
import com.nowcoder.community.search.domain.service.PostSearchDomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchApplicationService {

    private final PostSearchRepository postSearchRepository;
    private final PostSearchDomainService postSearchDomainService;
    private final SearchPolicyProperties searchPolicyProperties;
    private final FeatureFlagDecisions featureFlags;
    private final DegradationDecisions degradationDecisions;

    @Autowired
    public SearchApplicationService(
            PostSearchRepository postSearchRepository,
            PostSearchDomainService postSearchDomainService,
            SearchPolicyProperties searchPolicyProperties,
            FeatureFlagDecisions featureFlags,
            DegradationDecisions degradationDecisions
    ) {
        this.postSearchRepository = postSearchRepository;
        this.postSearchDomainService = postSearchDomainService;
        this.searchPolicyProperties = searchPolicyProperties == null ? new SearchPolicyProperties() : searchPolicyProperties;
        this.featureFlags = featureFlags == null ? new FeatureFlagDecisions(new FeatureFlagProperties()) : featureFlags;
        this.degradationDecisions = degradationDecisions == null ? new DegradationDecisions(new DegradationProperties()) : degradationDecisions;
    }

    public SearchApplicationService(
            PostSearchRepository postSearchRepository,
            PostSearchDomainService postSearchDomainService,
            SearchPolicyProperties searchPolicyProperties
    ) {
        this(
                postSearchRepository,
                postSearchDomainService,
                searchPolicyProperties,
                new FeatureFlagDecisions(new FeatureFlagProperties()),
                new DegradationDecisions(new DegradationProperties())
        );
    }

    public SearchApplicationService(
            PostSearchRepository postSearchRepository,
            PostSearchDomainService postSearchDomainService
    ) {
        this(postSearchRepository, postSearchDomainService, new SearchPolicyProperties());
    }

    public List<SearchPostResult> searchPosts(SearchPostsCommand command) {
        if (!featureFlags.enabledOrDefault("search", true)) {
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
            if (searchPolicyProperties.getDegradation().isEnabled()
                    || "best-effort".equals(degradationDecisions.mode("search"))) {
                return List.of();
            }
            throw e;
        }
    }

    public void syncPostProjection(SyncPostProjectionCommand command) {
        if (!postSearchDomainService.shouldIndex(command.postId(), command.status())) {
            postSearchRepository.delete(command.postId());
            return;
        }
        postSearchRepository.save(toDocument(command));
    }

    public void deletePost(DeleteIndexedPostCommand command) {
        if (command.postId() == null) {
            return;
        }
        postSearchRepository.delete(command.postId());
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
}
