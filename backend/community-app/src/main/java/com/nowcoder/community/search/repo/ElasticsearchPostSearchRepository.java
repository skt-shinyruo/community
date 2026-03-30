package com.nowcoder.community.search.repo;

// ES 实现：基于 alias 写入/查询，并支持指定索引写入。
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.search.dto.SearchPostItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
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
        EsPostDocument doc = toDocument(post);
        if (doc == null) {
            return;
        }
        operations.save(doc);
    }

    @Override
    public void upsertToIndex(PostPayload post, String indexName) {
        EsPostDocument doc = toDocument(post);
        if (doc == null) {
            return;
        }
        if (!StringUtils.hasText(indexName)) {
            operations.save(doc);
            return;
        }
        operations.save(doc, IndexCoordinates.of(indexName));
    }

    @Override
    public void delete(int postId) {
        if (postId <= 0) {
            return;
        }
        operations.delete(String.valueOf(postId), EsPostDocument.class);
    }

    @Override
    public void deleteFromIndex(int postId, String indexName) {
        if (postId <= 0) {
            return;
        }
        if (!StringUtils.hasText(indexName)) {
            operations.delete(String.valueOf(postId), EsPostDocument.class);
            return;
        }
        operations.delete(String.valueOf(postId), IndexCoordinates.of(indexName));
    }

    @Override
    public List<SearchPostItem> search(String keyword, Integer categoryId, String tag, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));

        String k = StringUtils.hasText(keyword) ? keyword.trim() : "";

        Criteria criteria;
        if (StringUtils.hasText(k)) {
            criteria = new Criteria("title").contains(k).or(new Criteria("content").contains(k));
        } else {
            // match-all baseline：便于叠加 taxonomy 过滤
            criteria = new Criteria("postId").exists();
        }

        if (categoryId != null && categoryId > 0) {
            criteria = criteria.and(new Criteria("categoryId").is(categoryId));
        }
        String safeTag = StringUtils.hasText(tag) ? tag.trim() : "";
        if (safeTag.startsWith("#")) {
            safeTag = safeTag.substring(1).trim();
        }
        if (StringUtils.hasText(safeTag)) {
            criteria = criteria.and(new Criteria("tags").is(safeTag));
        }

        Query query = new CriteriaQuery(criteria);

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

    @Override
    public void clearIndex(String indexName) {
        if (!StringUtils.hasText(indexName)) {
            clear();
            return;
        }
        var indexOps = operations.indexOps(IndexCoordinates.of(indexName));
        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.create();
        indexOps.putMapping(operations.indexOps(EsPostDocument.class).createMapping());
    }

    private SearchPostItem toItem(SearchHit<EsPostDocument> hit, String keyword) {
        EsPostDocument doc = hit.getContent();
        SearchPostItem item = new SearchPostItem();
        if (doc != null) {
            item.setPostId(doc.getPostId() == null ? 0 : doc.getPostId());
            item.setUserId(doc.getUserId() == null ? 0 : doc.getUserId());
            item.setCategoryId(doc.getCategoryId());
            item.setTags(doc.getTags() == null ? List.of() : doc.getTags());
            item.setTitle(doc.getTitle());
            item.setCreateTime(doc.getCreateTime() == null ? null : Instant.ofEpochMilli(doc.getCreateTime()));
            item.setScore(doc.getScore());
            if (StringUtils.hasText(keyword)) {
                item.setHighlightedTitle(KeywordHighlightSupport.highlight(doc.getTitle(), keyword));
                item.setHighlightedContent(KeywordHighlightSupport.highlight(doc.getContent(), keyword));
            }
        }
        return item;
    }

    private EsPostDocument toDocument(PostPayload post) {
        if (post == null || post.getPostId() <= 0) {
            return null;
        }
        EsPostDocument doc = new EsPostDocument();
        doc.setPostId(post.getPostId());
        doc.setUserId(post.getUserId());
        doc.setCategoryId(post.getCategoryId());
        doc.setTags(post.getTags() == null ? List.of() : post.getTags());
        doc.setTitle(post.getTitle());
        doc.setContent(post.getContent());
        doc.setType(post.getType());
        doc.setStatus(post.getStatus());
        doc.setCreateTime(post.getCreateTime() == null ? null : post.getCreateTime().toEpochMilli());
        doc.setScore(post.getScore());
        return doc;
    }
}
