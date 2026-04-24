package com.nowcoder.community.content.service;

import com.nowcoder.community.content.dto.HotTagResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TagApplicationService {

    private final TagService tagService;

    public TagApplicationService(TagService tagService) {
        this.tagService = tagService;
    }

    public List<HotTagResponse> listHotTagResponses(Integer limit) {
        return tagService.listHotTagResponses(limit);
    }

    public List<HotTagResponse> suggestTagResponses(String q, Integer limit) {
        return tagService.suggestTagResponses(q, limit);
    }
}
