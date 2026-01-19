package com.nowcoder.community.search.service;

import com.nowcoder.community.search.api.dto.SearchPostItem;
import com.nowcoder.community.search.api.dto.ContentPostScanResponse;
import com.nowcoder.community.search.config.ContentServiceClientProperties;
import com.nowcoder.community.search.repo.PostSearchRepository;
import com.nowcoder.community.common.event.payload.PostPayload;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PostSearchService {

    private final PostSearchRepository postSearchRepository;
    private final ContentServiceClient contentServiceClient;
    private final int scanPageSize;

    public PostSearchService(PostSearchRepository postSearchRepository, ContentServiceClient contentServiceClient, ContentServiceClientProperties properties) {
        this.postSearchRepository = postSearchRepository;
        this.contentServiceClient = contentServiceClient;
        this.scanPageSize = Math.min(1000, Math.max(1, properties.getPageSize()));
    }

    public List<SearchPostItem> search(String keyword, Integer page, Integer size) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));
        String k = StringUtils.hasText(keyword) ? keyword.trim() : "";
        return postSearchRepository.search(k, p, s);
    }

    public int clearAndReindexFromContentService() {
        postSearchRepository.clear();

        int total = 0;
        int afterId = 0;

        while (true) {
            ContentPostScanResponse page = contentServiceClient.scanPosts(afterId, scanPageSize);
            if (page == null || page.getItems() == null || page.getItems().isEmpty()) {
                break;
            }

            for (PostPayload post : page.getItems()) {
                postSearchRepository.upsert(post);
                total++;
            }

            int nextAfterId = page.getNextAfterId();
            if (nextAfterId <= afterId) {
                // 防御：避免服务端 bug 导致 afterId 不推进而死循环
                break;
            }
            afterId = nextAfterId;

            if (!page.isHasMore()) {
                break;
            }
        }

        return total;
    }
}
