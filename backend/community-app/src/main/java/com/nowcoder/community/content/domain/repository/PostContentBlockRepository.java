package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.PostContentBlock;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PostContentBlockRepository {

    void replaceBlocks(UUID postId, List<PostContentBlock> blocks);

    List<PostContentBlock> listByPostId(UUID postId);

    Map<UUID, List<PostContentBlock>> listByPostIds(List<UUID> postIds);
}
