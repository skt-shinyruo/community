package com.nowcoder.community.search.domain.repository;

import com.nowcoder.community.search.domain.model.PostSearchDocument;
import com.nowcoder.community.search.domain.model.PostSearchHit;
import com.nowcoder.community.search.domain.model.PostSearchQuery;

import java.util.List;
import java.util.UUID;

public interface PostSearchRepository {

    void save(PostSearchDocument post);

    void tombstone(UUID postId, long aggregateVersion);

    List<PostSearchHit> search(PostSearchQuery query);
}
