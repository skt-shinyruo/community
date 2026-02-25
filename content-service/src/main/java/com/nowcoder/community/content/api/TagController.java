package com.nowcoder.community.content.api;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.content.api.dto.HotTagResponse;
import com.nowcoder.community.content.entity.HotTag;
import com.nowcoder.community.content.service.TagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("/hot")
    public Result<List<HotTagResponse>> hot(@RequestParam(required = false) Integer limit) {
        List<HotTag> tags = tagService.listHotTags(limit);
        List<HotTagResponse> resp = tags.stream().map(TagController::toResp).toList();
        return Result.ok(resp);
    }

    @GetMapping("/suggest")
    public Result<List<HotTagResponse>> suggest(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit
    ) {
        List<HotTag> tags = tagService.suggestTags(q, limit);
        List<HotTagResponse> resp = tags.stream().map(TagController::toResp).toList();
        return Result.ok(resp);
    }

    private static HotTagResponse toResp(HotTag t) {
        HotTagResponse r = new HotTagResponse();
        r.setName(t.getName());
        r.setUseCount(t.getUseCount());
        return r;
    }
}
