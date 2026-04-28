package com.nowcoder.community.content.application.port;

import com.nowcoder.community.content.domain.model.HotTag;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface TagContentPort {

    List<HotTag> listHotTags(Integer limit);

    List<HotTag> suggestTags(String q, Integer limit);

    Map<UUID, List<String>> getTagsByPostIds(List<UUID> postIds);

    List<String> bindTagsToPost(UUID postId, List<String> rawTags);

    List<String> replaceTagsForPost(UUID postId, List<String> rawTags);

    List<String> normalizeTags(List<String> rawTags);
}
