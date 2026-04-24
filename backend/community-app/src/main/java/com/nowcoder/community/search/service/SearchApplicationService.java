package com.nowcoder.community.search.service;

import com.nowcoder.community.search.dto.SearchPostItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SearchApplicationService {

    private final PostSearchService postSearchService;

    public SearchApplicationService(PostSearchService postSearchService) {
        this.postSearchService = postSearchService;
    }

    public List<SearchPostItem> searchPosts(String keyword, UUID categoryId, String tag, Integer page, Integer size) {
        return postSearchService.search(keyword, categoryId, tag, page, size);
    }
}
