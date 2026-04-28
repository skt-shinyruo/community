package com.nowcoder.community.search.infrastructure.persistence;

// ES 实现：基于 alias 写入/查询，并支持指定索引写入。
import com.nowcoder.community.search.domain.model.PostSearchDocument;
import com.nowcoder.community.search.domain.model.PostSearchHit;
import com.nowcoder.community.search.domain.model.PostSearchQuery;
import com.nowcoder.community.search.domain.repository.PostSearchRepository;
import com.nowcoder.community.search.domain.service.KeywordHighlightSupport;
import com.nowcoder.community.search.infrastructure.persistence.dataobject.EsPostDocument;
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
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "search.storage", havingValue = "es")
public class ElasticsearchPostSearchRepository implements PostSearchRepository {

    private final ElasticsearchOperations operations;

    public ElasticsearchPostSearchRepository(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @Override
    public void save(PostSearchDocument post) {
        EsPostDocument doc = toDocument(post);
        if (doc == null) {
            return;
        }
        operations.save(doc);
    }

    @Override
    public void saveToIndex(PostSearchDocument post, String indexName) {
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
    public void delete(UUID postId) {
        if (postId == null) {
            return;
        }
        operations.delete(postId.toString(), EsPostDocument.class);
    }

    @Override
    public void deleteFromIndex(UUID postId, String indexName) {
        if (postId == null) {
            return;
        }
        if (!StringUtils.hasText(indexName)) {
            operations.delete(postId.toString(), EsPostDocument.class);
            return;
        }
        operations.delete(postId.toString(), IndexCoordinates.of(indexName));
    }

    @Override
    public List<PostSearchHit> search(PostSearchQuery query) {
        int p = Math.max(0, query.page());
        int s = Math.min(50, Math.max(1, query.size()));

        String k = StringUtils.hasText(query.keyword()) ? query.keyword().trim() : "";

        Criteria criteria;
        if (StringUtils.hasText(k)) {
            criteria = new Criteria("title").contains(k).or(new Criteria("content").contains(k));
        } else {
            // match-all baseline：便于叠加 taxonomy 过滤
            criteria = new Criteria("postId").exists();
        }

        if (query.categoryId() != null) {
            criteria = criteria.and(new Criteria("categoryId").is(query.categoryId().toString()));
        }
        String safeTag = StringUtils.hasText(query.tag()) ? query.tag().trim() : "";
        if (safeTag.startsWith("#")) {
            safeTag = safeTag.substring(1).trim();
        }
        if (StringUtils.hasText(safeTag)) {
            criteria = criteria.and(new Criteria("tags").is(safeTag));
        }

        Query criteriaQuery = new CriteriaQuery(criteria);

        criteriaQuery.setPageable(PageRequest.of(p, s));
        criteriaQuery.addSort(Sort.by(Sort.Order.desc("score"), Sort.Order.desc("createTime")));

        SearchHits<EsPostDocument> hits = operations.search(criteriaQuery, EsPostDocument.class);
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

    private PostSearchHit toItem(SearchHit<EsPostDocument> hit, String keyword) {
        EsPostDocument doc = hit.getContent();
        if (doc == null) {
            return new PostSearchHit(null, null, null, List.of(), null, null, null, null, null);
        }
        String highlightedTitle = null;
        String highlightedContent = null;
        if (StringUtils.hasText(keyword)) {
            highlightedTitle = KeywordHighlightSupport.highlight(doc.getTitle(), keyword);
            highlightedContent = KeywordHighlightSupport.highlight(doc.getContent(), keyword);
        }
        return new PostSearchHit(
                parseUuid(doc.getPostId()),
                parseUuid(doc.getUserId()),
                parseUuid(doc.getCategoryId()),
                doc.getTags() == null ? List.of() : doc.getTags(),
                doc.getTitle(),
                highlightedTitle,
                highlightedContent,
                doc.getCreateTime() == null ? null : Instant.ofEpochMilli(doc.getCreateTime()),
                doc.getScore()
        );
    }

    private EsPostDocument toDocument(PostSearchDocument post) {
        if (post == null || post.postId() == null) {
            return null;
        }
        EsPostDocument doc = new EsPostDocument();
        doc.setPostId(post.postId().toString());
        doc.setUserId(post.userId() == null ? null : post.userId().toString());
        doc.setCategoryId(post.categoryId() == null ? null : post.categoryId().toString());
        doc.setTags(post.tags() == null ? List.of() : post.tags());
        doc.setTitle(post.title());
        doc.setContent(post.content());
        doc.setType(post.type());
        doc.setStatus(post.status());
        doc.setCreateTime(post.createTime() == null ? null : post.createTime().toEpochMilli());
        doc.setScore(post.score());
        return doc;
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
