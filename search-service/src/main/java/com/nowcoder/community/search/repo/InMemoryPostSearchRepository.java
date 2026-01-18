package com.nowcoder.community.search.repo;

import com.nowcoder.community.common.event.payload.PostPayload;
import com.nowcoder.community.search.api.dto.SearchPostItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "search.storage", havingValue = "memory", matchIfMissing = true)
public class InMemoryPostSearchRepository implements PostSearchRepository {

    private final Map<Integer, PostPayload> store = new ConcurrentHashMap<>();

    @Override
    public void upsert(PostPayload post) {
        store.put(post.getPostId(), post);
    }

    @Override
    public void delete(int postId) {
        store.remove(postId);
    }

    @Override
    public List<SearchPostItem> search(String keyword, int page, int size) {
        String k = keyword == null ? "" : keyword.trim();
        List<PostPayload> matched = new ArrayList<>();
        for (PostPayload post : store.values()) {
            if (!StringUtils.hasText(k)) {
                matched.add(post);
                continue;
            }
            if ((post.getTitle() != null && post.getTitle().contains(k)) || (post.getContent() != null && post.getContent().contains(k))) {
                matched.add(post);
            }
        }
        matched.sort(Comparator.comparing(PostPayload::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));
        int from = Math.max(0, page * size);
        int to = Math.min(matched.size(), from + size);
        List<SearchPostItem> items = new ArrayList<>();
        for (PostPayload post : matched.subList(from, to)) {
            SearchPostItem item = new SearchPostItem();
            item.setPostId(post.getPostId());
            item.setTitle(post.getTitle());
            item.setCreateTime(post.getCreateTime());
            item.setScore(post.getScore());
            if (StringUtils.hasText(k)) {
                item.setHighlightedTitle(highlight(post.getTitle(), k));
                item.setHighlightedContent(highlight(post.getContent(), k));
            }
            items.add(item);
        }
        return items;
    }

    @Override
    public void clear() {
        store.clear();
    }

    private String highlight(String text, String keyword) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(keyword)) {
            return text;
        }
        return text.replace(keyword, "<em>" + keyword + "</em>");
    }
}

