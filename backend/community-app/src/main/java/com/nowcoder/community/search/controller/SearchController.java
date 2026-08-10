package com.nowcoder.community.search.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.search.application.SearchApplicationService;
import com.nowcoder.community.search.application.SearchApplicationService.SearchPostResult;
import com.nowcoder.community.search.application.SearchApplicationService.SearchPostsCommand;
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
    public Result<List<SearchPostResult>> searchPosts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(searchApplicationService.searchPosts(
                new SearchPostsCommand(keyword, categoryId, tag, page, size)));
    }
}
