package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.repository.TagContentRepository;
import com.nowcoder.community.content.application.result.HotTagResult;
import com.nowcoder.community.content.domain.model.HotTag;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TagApplicationService {

    private final TagContentRepository tagContentPort;

    public TagApplicationService(TagContentRepository tagContentPort) {
        this.tagContentPort = tagContentPort;
    }

    public List<HotTagResult> listHotTags(Integer limit) {
        return tagContentPort.listHotTags(limit).stream()
                .map(this::toResult)
                .toList();
    }

    public List<HotTagResult> suggestTags(String q, Integer limit) {
        return tagContentPort.suggestTags(q, limit).stream()
                .map(this::toResult)
                .toList();
    }

    private HotTagResult toResult(HotTag hotTag) {
        return new HotTagResult(hotTag.getName(), hotTag.getUseCount());
    }
}
