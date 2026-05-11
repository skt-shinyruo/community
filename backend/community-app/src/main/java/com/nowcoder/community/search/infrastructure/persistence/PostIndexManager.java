package com.nowcoder.community.search.infrastructure.persistence;

// ES 索引管理器：负责 alias 初始化、蓝绿切换与旧索引清理。
import com.nowcoder.community.search.domain.repository.PostSearchIndexRepository;
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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "search.storage", havingValue = "es")
public class PostIndexManager implements PostSearchIndexRepository {

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
    private final int keepHistory;

    public PostIndexManager(
            ElasticsearchOperations operations,
            @Value("${search.index.prefix:community_posts_v}") String indexPrefix,
            @Value("${search.index.keep-history:2}") int keepHistory
    ) {
        this.operations = operations;
        this.indexPrefix = StringUtils.hasText(indexPrefix) ? indexPrefix.trim() : EsPostDocument.INDEX_PREFIX;
        this.keepHistory = Math.max(1, keepHistory);
    }

    @Override
    public void ensureAliasReady() {
        var aliasOps = operations.indexOps(EsPostDocument.class);
        if (aliasOps.exists() && hasRequiredSearchMapping(aliasOps.getMapping())) {
            return;
        }
        String indexName = createNewIndex();
        switchAliasTo(indexName);
    }

    @Override
    public String createNewIndex() {
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

    @Override
    public void switchAliasTo(String newIndex) {
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

    @Override
    public void cleanupOldIndices() {
        List<String> managed = listManagedIndices();
        if (managed.isEmpty()) {
            return;
        }
        managed.sort(Comparator.naturalOrder());

        Set<String> keep = new HashSet<>(resolveAliasIndices());
        int start = Math.max(0, managed.size() - keepHistory);
        keep.addAll(managed.subList(start, managed.size()));

        for (String index : managed) {
            if (keep.contains(index)) {
                continue;
            }
            operations.indexOps(IndexCoordinates.of(index)).delete();
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
        List<IndexInformation> infos = operations.indexOps(IndexCoordinates.of(indexPrefix + "*")).getInformation();
        List<String> names = new ArrayList<>();
        for (IndexInformation info : infos) {
            if (info != null && StringUtils.hasText(info.getName()) && info.getName().startsWith(indexPrefix)) {
                names.add(info.getName());
            }
        }
        return names;
    }

    private void createIndexWithMapping(String indexName) {
        var indexOps = operations.indexOps(IndexCoordinates.of(indexName));
        if (!indexOps.exists()) {
            indexOps.create();
            indexOps.putMapping(operations.indexOps(EsPostDocument.class).createMapping());
        }
    }

    @SuppressWarnings("unchecked")
    private boolean hasRequiredSearchMapping(Map<String, Object> mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return false;
        }
        Object properties = mapping.get("properties");
        if (!(properties instanceof Map<?, ?> propertiesMap)) {
            return false;
        }
        for (String field : REQUIRED_SEARCH_FIELDS) {
            if (!propertiesMap.containsKey(field)) {
                return false;
            }
        }
        return true;
    }
}
