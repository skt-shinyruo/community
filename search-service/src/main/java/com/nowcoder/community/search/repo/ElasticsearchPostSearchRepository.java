package com.nowcoder.community.search.repo;

import com.nowcoder.community.common.event.payload.PostPayload;
import com.nowcoder.community.search.api.dto.SearchPostItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
@ConditionalOnProperty(name = "search.storage", havingValue = "es")
public class ElasticsearchPostSearchRepository implements PostSearchRepository {

    private final ElasticsearchOperations operations;

    public ElasticsearchPostSearchRepository(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @Override
    public void upsert(PostPayload post) {
        if (post == null || post.getPostId() <= 0) {
            return;
        }
        EsPostDocument doc = new EsPostDocument();
        doc.setPostId(post.getPostId());
        doc.setUserId(post.getUserId());
        doc.setTitle(post.getTitle());
        doc.setContent(post.getContent());
        doc.setType(post.getType());
        doc.setStatus(post.getStatus());
        doc.setCreateTime(post.getCreateTime());
        doc.setScore(post.getScore());
        operations.save(doc);
    }

    @Override
    public void delete(int postId) {
        if (postId <= 0) {
            return;
        }
        operations.delete(String.valueOf(postId), EsPostDocument.class);
    }

    @Override
    public List<SearchPostItem> search(String keyword, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));

        Query query;
        String k = StringUtils.hasText(keyword) ? keyword.trim() : "";
        if (!StringUtils.hasText(k)) {
            query = Query.findAll();
        } else {
            Criteria criteria = new Criteria("title").contains(k).or(new Criteria("content").contains(k));
            query = new CriteriaQuery(criteria);
        }

        query.setPageable(PageRequest.of(p, s));
        query.addSort(Sort.by(Sort.Order.desc("score"), Sort.Order.desc("createTime")));

        SearchHits<EsPostDocument> hits = operations.search(query, EsPostDocument.class);
        return hits.getSearchHits().stream().map(hit -> toItem(hit, k)).toList();
    }

    @Override
    public void clear() {
        var indexOps = operations.indexOps(EsPostDocument.class);
        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.createWithMapping();
    }

    private SearchPostItem toItem(SearchHit<EsPostDocument> hit, String keyword) {
        EsPostDocument doc = hit.getContent();
        SearchPostItem item = new SearchPostItem();
        if (doc != null) {
            item.setPostId(doc.getPostId() == null ? 0 : doc.getPostId());
            item.setTitle(doc.getTitle());
            item.setCreateTime(doc.getCreateTime());
            item.setScore(doc.getScore());
            if (StringUtils.hasText(keyword)) {
                item.setHighlightedTitle(highlight(doc.getTitle(), keyword));
                item.setHighlightedContent(highlight(doc.getContent(), keyword));
            }
        }
        return item;
    }

    private String highlight(String text, String keyword) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(keyword)) {
            return text;
        }
        return text.replace(keyword, "<em>" + keyword + "</em>");
    }
}

