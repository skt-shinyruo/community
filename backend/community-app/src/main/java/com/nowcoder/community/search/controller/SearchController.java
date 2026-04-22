package com.nowcoder.community.search.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.search.dto.SearchPostItem;
import com.nowcoder.community.search.service.PostSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final PostSearchService postSearchService;

    public SearchController(PostSearchService postSearchService) {
        this.postSearchService = postSearchService;
    }

    @GetMapping("/posts")
    public Result<List<SearchPostItem>> searchPosts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(postSearchService.search(keyword, categoryId, tag, page, size));
    }
}
