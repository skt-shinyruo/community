package com.nowcoder.community.search.api;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.search.api.dto.ReindexResponse;
import com.nowcoder.community.search.service.PostSearchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/search")
public class InternalSearchController {

    private final PostSearchService postSearchService;

    public InternalSearchController(PostSearchService postSearchService) {
        this.postSearchService = postSearchService;
    }

    @PostMapping("/reindex")
    public Result<ReindexResponse> reindex() {
        int count = postSearchService.clearAndReindexFromContentService();
        return Result.ok(new ReindexResponse(count));
    }
}
