package com.nowcoder.community.search.repo;

import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.search.dto.SearchPostItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "search.storage", havingValue = "memory", matchIfMissing = false)
public class InMemoryPostSearchRepository implements PostSearchRepository {

    private final Map<UUID, PostPayload> store = new ConcurrentHashMap<>();

    @Override
    public void upsert(PostPayload post) {
        store.put(post.getPostId(), post);
    }

    @Override
    public void delete(UUID postId) {
        store.remove(postId);
    }

    @Override
    public List<SearchPostItem> search(String keyword, UUID categoryId, String tag, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));

        String k = keyword == null ? "" : keyword.trim();
        String kLower = StringUtils.hasText(k) ? k.toLowerCase(Locale.ROOT) : "";
        UUID cid = categoryId;
        String safeTag = tag == null ? "" : tag.trim();
        if (safeTag.startsWith("#")) {
            safeTag = safeTag.substring(1).trim();
        }
        String tagKey = safeTag.isBlank() ? "" : safeTag.toLowerCase(Locale.ROOT);

        List<PostPayload> matched = new ArrayList<>();
        for (PostPayload post : store.values()) {
            if (cid != null) {
                UUID pc = post.getCategoryId();
                if (pc == null || !cid.equals(pc)) {
                    continue;
                }
            }
            if (!tagKey.isBlank()) {
                List<String> tags = post.getTags();
                boolean ok = false;
                if (tags != null) {
                    for (String t : tags) {
                        if (t != null && t.toLowerCase(Locale.ROOT).equals(tagKey)) {
                            ok = true;
                            break;
                        }
                    }
                }
                if (!ok) {
                    continue;
                }
            }

            if (!StringUtils.hasText(k)) {
                matched.add(post);
                continue;
            }
            if (containsIgnoreCase(post.getTitle(), kLower) || containsIgnoreCase(post.getContent(), kLower)) {
                matched.add(post);
            }
        }
        matched.sort(Comparator.comparing(PostPayload::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));
        long fromLong = (long) p * (long) s;
        if (fromLong >= matched.size()) {
            return List.of();
        }
        int from = (int) fromLong;
        int to = (int) Math.min((long) matched.size(), fromLong + (long) s);
        List<SearchPostItem> items = new ArrayList<>();
        for (PostPayload post : matched.subList(from, to)) {
            SearchPostItem item = new SearchPostItem();
            item.setPostId(post.getPostId());
            item.setUserId(post.getUserId());
            item.setCategoryId(post.getCategoryId());
            item.setTags(post.getTags() == null ? List.of() : post.getTags());
            item.setTitle(post.getTitle());
            item.setCreateTime(post.getCreateTime());
            item.setScore(post.getScore());
            if (StringUtils.hasText(k)) {
                item.setHighlightedTitle(KeywordHighlightSupport.highlight(post.getTitle(), k));
                item.setHighlightedContent(KeywordHighlightSupport.highlight(post.getContent(), k));
            }
            items.add(item);
        }
        return items;
    }

    @Override
    public void clear() {
        store.clear();
    }

    private boolean containsIgnoreCase(String text, String keywordLower) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(keywordLower)) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(keywordLower);
    }
}
