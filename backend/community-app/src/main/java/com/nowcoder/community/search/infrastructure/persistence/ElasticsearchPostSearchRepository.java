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
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.ScriptType;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "search.storage", havingValue = "es")
public class ElasticsearchPostSearchRepository implements PostSearchRepository {

    private static final int DELETED_STATUS = 2;
    private static final int VERSION_CONFLICT_RETRIES = 5;
    private static final String MONOTONIC_PROJECTION_SCRIPT = """
            if (ctx.op == 'create') {
                ctx._source.clear();
                ctx._source.putAll(params.document);
            } else if (ctx._source.aggregateVersion == null
                    || params.aggregateVersion > ctx._source.aggregateVersion) {
                ctx._source.clear();
                ctx._source.putAll(params.document);
            } else if (params.aggregateVersion == ctx._source.aggregateVersion
                    && (ctx._source.scoreVersion == null
                        || params.scoreVersion > ctx._source.scoreVersion)) {
                ctx._source.score = params.document.score;
                ctx._source.scoreVersion = params.scoreVersion;
            } else {
                ctx.op = 'noop';
            }
            """;

    private final ElasticsearchOperations operations;

    public ElasticsearchPostSearchRepository(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @Override
    public void save(PostSearchDocument post) {
        saveMonotonically(post, IndexCoordinates.of(EsPostDocument.INDEX_ALIAS));
    }

    @Override
    public void saveToIndex(PostSearchDocument post, String indexName) {
        if (!StringUtils.hasText(indexName)) {
            save(post);
            return;
        }
        saveMonotonically(post, IndexCoordinates.of(indexName));
    }

    @Override
    public void tombstone(UUID postId, long aggregateVersion) {
        if (postId == null) {
            return;
        }
        save(PostSearchDocument.tombstone(postId, aggregateVersion));
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
        criteria = criteria.and(new Criteria("status").not().is(DELETED_STATUS));

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
        doc.setAggregateVersion(post.aggregateVersion());
        doc.setScoreVersion(post.scoreVersion());
        doc.setCreateTime(post.createTime() == null ? null : post.createTime().toEpochMilli());
        doc.setScore(post.score());
        return doc;
    }

    private void saveMonotonically(PostSearchDocument post, IndexCoordinates index) {
        EsPostDocument doc = toDocument(post);
        if (doc == null) {
            return;
        }
        Document source = toSource(doc);
        UpdateQuery update = UpdateQuery.builder(doc.getPostId())
                .withScript(MONOTONIC_PROJECTION_SCRIPT)
                .withScriptType(ScriptType.INLINE)
                .withLang("painless")
                .withParams(Map.of(
                        "aggregateVersion", post.aggregateVersion(),
                        "scoreVersion", post.scoreVersion(),
                        "document", source
                ))
                .withUpsert(source)
                .withScriptedUpsert(true)
                .withRetryOnConflict(VERSION_CONFLICT_RETRIES)
                .build();
        operations.update(update, index);
    }

    private Document toSource(EsPostDocument doc) {
        Document source = Document.create();
        source.put("postId", doc.getPostId());
        source.put("userId", doc.getUserId());
        source.put("categoryId", doc.getCategoryId());
        source.put("tags", doc.getTags());
        source.put("title", doc.getTitle());
        source.put("content", doc.getContent());
        source.put("type", doc.getType());
        source.put("status", doc.getStatus());
        source.put("aggregateVersion", doc.getAggregateVersion());
        source.put("scoreVersion", doc.getScoreVersion());
        source.put("createTime", doc.getCreateTime());
        source.put("score", doc.getScore());
        return source;
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
