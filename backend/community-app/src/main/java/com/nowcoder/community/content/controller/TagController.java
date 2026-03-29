package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.dto.HotTagResponse;
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
        return Result.ok(tagService.listHotTagResponses(limit));
    }

    @GetMapping("/suggest")
    public Result<List<HotTagResponse>> suggest(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit
    ) {
        return Result.ok(tagService.suggestTagResponses(q, limit));
    }
}
