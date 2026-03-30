package com.nowcoder.community.search.repo;

// 帖子搜索存储接口：支持常规索引操作与可选的指定索引写入。
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.search.dto.SearchPostItem;

import java.util.List;

public interface PostSearchRepository {

    void upsert(PostPayload post);

    void delete(int postId);

    List<SearchPostItem> search(String keyword, Integer categoryId, String tag, int page, int size);

    void clear();

    default void upsertToIndex(PostPayload post, String indexName) {
        upsert(post);
    }

    default void deleteFromIndex(int postId, String indexName) {
        delete(postId);
    }

    default void clearIndex(String indexName) {
        clear();
    }
}
