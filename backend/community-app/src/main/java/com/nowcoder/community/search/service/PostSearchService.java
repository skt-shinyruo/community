package com.nowcoder.community.search.service;

// 帖子搜索服务：支持基于 alias 的零停机重建。
import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.search.config.PostScanProperties;
import com.nowcoder.community.search.dto.SearchPostItem;
import com.nowcoder.community.search.repo.PostIndexManager;
import com.nowcoder.community.search.repo.PostSearchRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PostSearchService {

    private final PostSearchRepository postSearchRepository;
    private final PostScanQueryApi postScanQueryApi;
    private final int scanPageSize;
    private final ObjectProvider<PostIndexManager> postIndexManagerProvider;

    public PostSearchService(
            PostSearchRepository postSearchRepository,
            PostScanQueryApi postScanQueryApi,
            PostScanProperties properties,
            ObjectProvider<PostIndexManager> postIndexManagerProvider
    ) {
        this.postSearchRepository = postSearchRepository;
        this.postScanQueryApi = postScanQueryApi;
        this.scanPageSize = Math.min(1000, Math.max(1, properties.getPageSize()));
        this.postIndexManagerProvider = postIndexManagerProvider;
    }

    public List<SearchPostItem> search(String keyword, Integer categoryId, String tag, Integer page, Integer size) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));
        String k = StringUtils.hasText(keyword) ? keyword.trim() : "";
        Integer cid = categoryId != null && categoryId > 0 ? categoryId : null;
        String safeTag = StringUtils.hasText(tag) ? tag.trim() : "";
        if (safeTag.startsWith("#")) {
            safeTag = safeTag.substring(1).trim();
        }
        if (!StringUtils.hasText(safeTag)) {
            safeTag = null;
        }
        return postSearchRepository.search(k, cid, safeTag, p, s);
    }

    public int clearAndReindexFromContentService() {
        PostIndexManager indexManager = postIndexManagerProvider.getIfAvailable();
        String targetIndex = null;
        if (indexManager != null) {
            indexManager.ensureAliasReady();
            targetIndex = indexManager.createNewIndex();
        } else {
            postSearchRepository.clear();
        }

        int total = 0;
        int afterId = 0;

        while (true) {
            PostScanView page = postScanQueryApi.scanPosts(afterId, scanPageSize);
            if (page == null || page.items().isEmpty()) {
                break;
            }

            for (PostScanView.PostProjectionView projection : page.items()) {
                if (targetIndex == null) {
                    postSearchRepository.upsert(PostSearchPayloadMapper.toPayload(projection));
                } else {
                    postSearchRepository.upsertToIndex(PostSearchPayloadMapper.toPayload(projection), targetIndex);
                }
                total++;
            }

            int nextAfterId = page.nextAfterId();
            if (nextAfterId <= afterId) {
                // 防御：避免服务端 bug 导致 afterId 不推进而死循环
                break;
            }
            afterId = nextAfterId;

            if (!page.hasMore()) {
                break;
            }
        }

        if (indexManager != null && targetIndex != null) {
            indexManager.switchAliasTo(targetIndex);
            indexManager.cleanupOldIndices();
        }

        return total;
    }
}
