package com.nowcoder.community.search.infrastructure.persistence;

import com.nowcoder.community.search.domain.model.PostSearchDocument;
import com.nowcoder.community.search.domain.model.PostSearchHit;
import com.nowcoder.community.search.domain.model.PostSearchQuery;
import com.nowcoder.community.search.domain.repository.PostSearchRepository;
import com.nowcoder.community.search.domain.service.KeywordHighlightSupport;
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

    private final Map<UUID, PostSearchDocument> store = new ConcurrentHashMap<>();

    @Override
    public void save(PostSearchDocument post) {
        if (post == null || post.postId() == null) {
            return;
        }
        store.put(post.postId(), post);
    }

    @Override
    public void delete(UUID postId) {
        store.remove(postId);
    }

    @Override
    public List<PostSearchHit> search(PostSearchQuery query) {
        int p = Math.max(0, query.page());
        int s = Math.min(50, Math.max(1, query.size()));

        String k = query.keyword() == null ? "" : query.keyword().trim();
        String kLower = StringUtils.hasText(k) ? k.toLowerCase(Locale.ROOT) : "";
        UUID cid = query.categoryId();
        String safeTag = query.tag() == null ? "" : query.tag().trim();
        if (safeTag.startsWith("#")) {
            safeTag = safeTag.substring(1).trim();
        }
        String tagKey = safeTag.isBlank() ? "" : safeTag.toLowerCase(Locale.ROOT);

        List<PostSearchDocument> matched = new ArrayList<>();
        for (PostSearchDocument post : store.values()) {
            if (cid != null) {
                UUID pc = post.categoryId();
                if (pc == null || !cid.equals(pc)) {
                    continue;
                }
            }
            if (!tagKey.isBlank()) {
                List<String> tags = post.tags();
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
            if (containsIgnoreCase(post.title(), kLower) || containsIgnoreCase(post.content(), kLower)) {
                matched.add(post);
            }
        }
        matched.sort(Comparator.comparing(PostSearchDocument::createTime, Comparator.nullsLast(Comparator.reverseOrder())));
        long fromLong = (long) p * (long) s;
        if (fromLong >= matched.size()) {
            return List.of();
        }
        int from = (int) fromLong;
        int to = (int) Math.min((long) matched.size(), fromLong + (long) s);
        List<PostSearchHit> items = new ArrayList<>();
        for (PostSearchDocument post : matched.subList(from, to)) {
            String highlightedTitle = null;
            String highlightedContent = null;
            if (StringUtils.hasText(k)) {
                highlightedTitle = KeywordHighlightSupport.highlight(post.title(), k);
                highlightedContent = KeywordHighlightSupport.highlight(post.content(), k);
            }
            items.add(new PostSearchHit(
                    post.postId(),
                    post.userId(),
                    post.categoryId(),
                    post.tags(),
                    post.title(),
                    highlightedTitle,
                    highlightedContent,
                    post.createTime(),
                    post.score()
            ));
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
