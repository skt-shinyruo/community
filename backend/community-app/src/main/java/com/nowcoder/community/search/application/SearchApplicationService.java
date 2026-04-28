package com.nowcoder.community.search.application;

import com.nowcoder.community.search.application.command.DeleteIndexedPostCommand;
import com.nowcoder.community.search.application.command.SearchPostsCommand;
import com.nowcoder.community.search.application.command.SyncPostProjectionCommand;
import com.nowcoder.community.search.application.result.SearchPostResult;
import com.nowcoder.community.search.domain.model.PostSearchDocument;
import com.nowcoder.community.search.domain.model.PostSearchHit;
import com.nowcoder.community.search.domain.model.PostSearchQuery;
import com.nowcoder.community.search.domain.repository.PostSearchRepository;
import com.nowcoder.community.search.domain.service.PostSearchDomainService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchApplicationService {

    private final PostSearchRepository postSearchRepository;
    private final PostSearchDomainService postSearchDomainService;

    public SearchApplicationService(
            PostSearchRepository postSearchRepository,
            PostSearchDomainService postSearchDomainService
    ) {
        this.postSearchRepository = postSearchRepository;
        this.postSearchDomainService = postSearchDomainService;
    }

    public List<SearchPostResult> searchPosts(SearchPostsCommand command) {
        PostSearchQuery query = postSearchDomainService.normalizeSearchQuery(
                command.keyword(),
                command.categoryId(),
                command.tag(),
                command.page(),
                command.size()
        );
        return postSearchRepository.search(query).stream()
                .map(this::toResult)
                .toList();
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
