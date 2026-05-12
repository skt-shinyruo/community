package com.nowcoder.community.search.infrastructure.persistence;

// ES 索引管理器：负责 alias 初始化。
import com.nowcoder.community.search.infrastructure.persistence.dataobject.EsPostDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.ResourceNotFoundException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "search.storage", havingValue = "es")
public class PostIndexManager {

    private static final DateTimeFormatter VERSION_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> REQUIRED_SEARCH_FIELDS = Set.of(
            "postId",
            "title",
            "content",
            "categoryId",
            "tags",
            "score",
            "createTime"
    );

    private final ElasticsearchOperations operations;
    private final String indexPrefix;

    public PostIndexManager(
            ElasticsearchOperations operations,
            @Value("${search.index.prefix:community_posts_v}") String indexPrefix
    ) {
        this.operations = operations;
        this.indexPrefix = StringUtils.hasText(indexPrefix) ? indexPrefix.trim() : EsPostDocument.INDEX_PREFIX;
    }

    public void ensureAliasReady() {
        var aliasOps = operations.indexOps(EsPostDocument.class);
        if (aliasOps.exists()) {
            Set<String> missingFields = missingRequiredSearchFields(aliasOps.getMapping());
            if (missingFields.isEmpty()) {
                return;
            }
            throw new IllegalStateException(
                    "search alias " + EsPostDocument.INDEX_ALIAS + " mapping is incompatible, missing fields: " + missingFields
            );
        }
        String indexName = createNewIndex();
        switchAliasTo(indexName);
    }

    private String createNewIndex() {
        String base = indexPrefix + VERSION_FORMAT.format(Instant.now().atZone(ZoneOffset.UTC));
        String indexName = base;
        int attempt = 0;
        while (operations.indexOps(IndexCoordinates.of(indexName)).exists()) {
            attempt++;
            indexName = base + "_" + attempt;
        }
        createIndexWithMapping(indexName);
        return indexName;
    }

    private void switchAliasTo(String newIndex) {
        if (!StringUtils.hasText(newIndex)) {
            return;
        }
        Set<String> current = resolveAliasIndices();
        AliasActions actions = new AliasActions();

        AliasActionParameters addParams = AliasActionParameters.builder()
                .withIndices(newIndex)
                .withAliases(EsPostDocument.INDEX_ALIAS)
                .withIsWriteIndex(true)
                .build();
        actions.add(new AliasAction.Add(addParams));

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

        operations.indexOps(IndexCoordinates.of(newIndex)).alias(actions);
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

    private void createIndexWithMapping(String indexName) {
        var indexOps = operations.indexOps(IndexCoordinates.of(indexName));
        if (!indexOps.exists()) {
            indexOps.create();
            indexOps.putMapping(operations.indexOps(EsPostDocument.class).createMapping());
        }
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
