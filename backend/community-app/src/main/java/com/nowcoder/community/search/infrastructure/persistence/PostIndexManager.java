package com.nowcoder.community.search.infrastructure.persistence;

// ES 索引管理器：负责 alias 初始化、蓝绿切换与历史索引清理。
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.nowcoder.community.search.infrastructure.persistence.dataobject.EsPostDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.ResourceNotFoundException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexInformation;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "search.storage", havingValue = "es")
public class PostIndexManager {

    private static final DateTimeFormatter VERSION_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int MAX_INDEX_NAME_ATTEMPTS = 100;
    private static final String AGGREGATE_VERSION_FIELD = "aggregateVersion";
    private static final String SCORE_VERSION_FIELD = "scoreVersion";
    private static final Set<String> ADDITIVE_VERSION_FIELDS = Set.of(
            AGGREGATE_VERSION_FIELD,
            SCORE_VERSION_FIELD
    );
    private static final Set<String> REQUIRED_SEARCH_FIELDS = Set.of(
            "postId",
            "title",
            "content",
            "categoryId",
            "tags",
            "status",
            AGGREGATE_VERSION_FIELD,
            SCORE_VERSION_FIELD,
            "score",
            "createTime"
    );

    private final ElasticsearchOperations operations;
    private final String indexPrefix;
    private final Pattern managedIndexPattern;
    private final int keepHistory;

    public PostIndexManager(
            ElasticsearchOperations operations,
            @Value("${search.index.prefix:community_posts_v}") String indexPrefix,
            @Value("${search.index.keep-history:2}") int keepHistory
    ) {
        this.operations = operations;
        this.indexPrefix = StringUtils.hasText(indexPrefix) ? indexPrefix.trim() : EsPostDocument.INDEX_PREFIX;
        this.managedIndexPattern = Pattern.compile(
                "^" + Pattern.quote(this.indexPrefix) + "\\d{14}(?:_\\d+)?$"
        );
        this.keepHistory = Math.max(0, keepHistory);
    }

    public void ensureAliasReady() {
        var aliasOps = operations.indexOps(EsPostDocument.class);
        if (aliasOps.exists()) {
            Set<String> missingFields = missingRequiredSearchFields(aliasOps.getMapping());
            if (missingFields.isEmpty()) {
                return;
            }
            if (ADDITIVE_VERSION_FIELDS.containsAll(missingFields)
                    && aliasOps.putMapping(aliasOps.createMapping())) {
                return;
            }
            throw new IllegalStateException(
                    "search alias " + EsPostDocument.INDEX_ALIAS + " mapping is incompatible, missing fields: " + missingFields
            );
        }
        String indexName = createNewIndex();
        switchAliasTo(indexName);
    }

    public String createNewIndex() {
        String base = indexPrefix + VERSION_FORMAT.format(Instant.now().atZone(ZoneOffset.UTC));
        for (int attempt = 0; attempt < MAX_INDEX_NAME_ATTEMPTS; attempt++) {
            String indexName = attempt == 0 ? base : base + "_" + attempt;
            if (createIndexWithMapping(indexName)) {
                return indexName;
            }
        }
        throw new IllegalStateException("could not allocate a unique search index name for " + base);
    }

    public void switchAliasTo(String newIndex) {
        if (!StringUtils.hasText(newIndex)) {
            return;
        }
        Set<String> current = resolveAliasIndices();
        AliasActions actions = new AliasActions();

        for (String oldIndex : current) {
            if (newIndex.equals(oldIndex)) {
                continue;
            }
            AliasActionParameters removeParams = AliasActionParameters.builder()
                    .withIndices(oldIndex)
                    .withAliases(EsPostDocument.INDEX_ALIAS)
                    .build();
            actions.add(new AliasAction.Remove(removeParams));
        }

        AliasActionParameters addParams = AliasActionParameters.builder()
                .withIndices(newIndex)
                .withAliases(EsPostDocument.INDEX_ALIAS)
                .withIsWriteIndex(true)
                .build();
        actions.add(new AliasAction.Add(addParams));

        if (!operations.indexOps(IndexCoordinates.of(newIndex)).alias(actions)) {
            throw new IllegalStateException("search alias switch was not acknowledged for " + newIndex);
        }
    }

    public void refreshIndex(String indexName) {
        if (StringUtils.hasText(indexName)) {
            operations.indexOps(IndexCoordinates.of(indexName)).refresh();
        }
    }

    public void deleteIndex(String indexName) {
        if (!StringUtils.hasText(indexName)) {
            return;
        }
        var indexOps = operations.indexOps(IndexCoordinates.of(indexName));
        if (indexOps.exists() && !indexOps.delete()) {
            throw new IllegalStateException("search index deletion was not acknowledged for " + indexName);
        }
    }

    public void cleanupOldIndices() {
        List<String> managed = listManagedIndices();
        if (managed.isEmpty()) {
            return;
        }
        Comparator<String> newestFirst = Comparator
                .comparing(this::managedIndexTimestamp)
                .thenComparing(this::managedIndexSuffix)
                .reversed();
        managed.sort(newestFirst);

        Set<String> keep = new HashSet<>(resolveAliasIndices());
        managed.stream()
                .filter(indexName -> !keep.contains(indexName))
                .limit(keepHistory)
                .forEach(keep::add);

        for (String indexName : managed) {
            if (!keep.contains(indexName)) {
                deleteIndex(indexName);
            }
        }
    }

    private Set<String> resolveAliasIndices() {
        try {
            return operations.indexOps(EsPostDocument.class)
                    .getAliases(EsPostDocument.INDEX_ALIAS)
                    .keySet();
        } catch (ResourceNotFoundException ignored) {
            return Set.of();
        }
    }

    private List<String> listManagedIndices() {
        List<IndexInformation> information = operations
                .indexOps(IndexCoordinates.of(indexPrefix + "*"))
                .getInformation();
        List<String> names = new ArrayList<>();
        if (information == null) {
            return names;
        }
        for (IndexInformation index : information) {
            if (index != null
                    && StringUtils.hasText(index.getName())
                    && managedIndexPattern.matcher(index.getName()).matches()) {
                names.add(index.getName());
            }
        }
        return names;
    }

    private boolean createIndexWithMapping(String indexName) {
        var indexOps = operations.indexOps(IndexCoordinates.of(indexName));
        if (indexOps.exists()) {
            return false;
        }
        try {
            if (!indexOps.create()) {
                if (indexOps.exists()) {
                    return false;
                }
                throw new IllegalStateException("search index creation was not acknowledged for " + indexName);
            }
        } catch (RuntimeException creationFailure) {
            if (isIndexNameConflict(creationFailure)) {
                return false;
            }
            throw creationFailure;
        }
        try {
            if (!indexOps.putMapping(operations.indexOps(EsPostDocument.class).createMapping())) {
                throw new IllegalStateException("search index mapping was not acknowledged for " + indexName);
            }
        } catch (RuntimeException mappingFailure) {
            try {
                if (!indexOps.delete()) {
                    mappingFailure.addSuppressed(new IllegalStateException(
                            "search index cleanup was not acknowledged for " + indexName
                    ));
                }
            } catch (RuntimeException cleanupFailure) {
                mappingFailure.addSuppressed(cleanupFailure);
            }
            throw mappingFailure;
        }
        return true;
    }

    private boolean isIndexNameConflict(RuntimeException failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ElasticsearchException elasticsearchFailure
                    && elasticsearchFailure.error() != null
                    && "resource_already_exists_exception".equals(elasticsearchFailure.error().type())) {
                return true;
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return false;
    }

    private String managedIndexTimestamp(String indexName) {
        int versionStart = indexPrefix.length();
        return indexName.substring(versionStart, versionStart + 14);
    }

    private BigInteger managedIndexSuffix(String indexName) {
        int suffixSeparator = indexPrefix.length() + 14;
        if (indexName.length() == suffixSeparator) {
            return BigInteger.ZERO;
        }
        return new BigInteger(indexName.substring(suffixSeparator + 1));
    }

    private Set<String> missingRequiredSearchFields(Map<String, Object> mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return REQUIRED_SEARCH_FIELDS;
        }
        Object properties = mapping.get("properties");
        if (!(properties instanceof Map<?, ?> propertiesMap)) {
            return REQUIRED_SEARCH_FIELDS;
        }
        return REQUIRED_SEARCH_FIELDS.stream()
                .filter(field -> !propertiesMap.containsKey(field))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
