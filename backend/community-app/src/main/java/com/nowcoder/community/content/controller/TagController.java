package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.application.result.HotTagResult;
import com.nowcoder.community.content.controller.dto.HotTagResponse;
import com.nowcoder.community.content.application.TagApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagApplicationService tagApplicationService;

    public TagController(TagApplicationService tagApplicationService) {
        this.tagApplicationService = tagApplicationService;
    }

    @GetMapping("/hot")
    public Result<List<HotTagResponse>> hot(@RequestParam(required = false) Integer limit) {
        return Result.ok(tagApplicationService.listHotTags(limit).stream()
                .map(this::toHotTagResponse)
                .toList());
    }

    @GetMapping("/suggest")
    public Result<List<HotTagResponse>> suggest(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit
    ) {
        return Result.ok(tagApplicationService.suggestTags(q, limit).stream()
                .map(this::toHotTagResponse)
                .toList());
    }

    private HotTagResponse toHotTagResponse(HotTagResult hotTag) {
        HotTagResponse response = new HotTagResponse();
        response.setName(hotTag.name());
        response.setUseCount(hotTag.useCount());
        return response;
    }
}
