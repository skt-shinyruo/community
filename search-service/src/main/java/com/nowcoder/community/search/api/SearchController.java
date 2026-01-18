package com.nowcoder.community.search.api;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.search.api.dto.SearchPostItem;
import com.nowcoder.community.search.api.dto.ReindexResponse;
import com.nowcoder.community.search.service.PostSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(postSearchService.search(keyword, page, size));
    }

    @PostMapping("/internal/reindex")
    public Result<ReindexResponse> reindex() {
        int count = postSearchService.clearAndReindexFromDb();
        return Result.ok(new ReindexResponse(count));
    }
}
