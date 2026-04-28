package com.nowcoder.community.search.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.search.application.SearchApplicationService;
import com.nowcoder.community.search.application.command.SearchPostsCommand;
import com.nowcoder.community.search.application.result.SearchPostResult;
import com.nowcoder.community.search.controller.dto.SearchPostItemResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchApplicationService searchApplicationService;

    public SearchController(SearchApplicationService searchApplicationService) {
        this.searchApplicationService = searchApplicationService;
    }

    @GetMapping("/posts")
    public Result<List<SearchPostItemResponse>> searchPosts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        List<SearchPostItemResponse> responses = searchApplicationService
                .searchPosts(new SearchPostsCommand(keyword, categoryId, tag, page, size))
                .stream()
                .map(this::toResponse)
                .toList();
        return Result.ok(responses);
    }

    private SearchPostItemResponse toResponse(SearchPostResult result) {
        SearchPostItemResponse response = new SearchPostItemResponse();
        response.setPostId(result.postId());
        response.setUserId(result.userId());
        response.setCategoryId(result.categoryId());
        response.setTags(result.tags());
        response.setTitle(result.title());
        response.setHighlightedTitle(result.highlightedTitle());
        response.setHighlightedContent(result.highlightedContent());
        response.setCreateTime(result.createTime());
        response.setScore(result.score());
        return response;
    }
}
