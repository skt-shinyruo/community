package com.nowcoder.community.search.repo;

import com.nowcoder.community.common.event.payload.PostPayload;
import com.nowcoder.community.search.api.dto.SearchPostItem;

import java.util.List;

public interface PostSearchRepository {

    void upsert(PostPayload post);

    void delete(int postId);

    List<SearchPostItem> search(String keyword, Integer categoryId, String tag, int page, int size);

    void clear();
}
