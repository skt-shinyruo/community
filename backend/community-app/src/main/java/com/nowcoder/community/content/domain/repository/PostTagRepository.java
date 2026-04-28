package com.nowcoder.community.content.domain.repository;

import java.util.List;
import java.util.UUID;

public interface PostTagRepository {

    List<String> bindTagsToPost(UUID postId, List<String> rawTags);

    List<String> replaceTagsForPost(UUID postId, List<String> rawTags);
}
