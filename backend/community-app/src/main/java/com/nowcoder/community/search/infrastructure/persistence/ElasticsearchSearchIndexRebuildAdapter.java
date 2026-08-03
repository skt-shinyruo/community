package com.nowcoder.community.search.infrastructure.persistence;

import com.nowcoder.community.search.application.SearchIndexRebuildPort;
import com.nowcoder.community.search.domain.model.PostSearchDocument;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "search.storage", havingValue = "es")
public class ElasticsearchSearchIndexRebuildAdapter implements SearchIndexRebuildPort {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSearchIndexRebuildAdapter.class);
    private static final int MAX_PAGE_SIZE = 1000;

    private final PostIndexManager indexManager;
    private final ElasticsearchPostSearchRepository postSearchRepository;
    private final SearchReindexTargetRegistry targetRegistry;
    private final ScheduledExecutorService targetRenewer;
    private final boolean ownsTargetRenewer;

    @Autowired
    public ElasticsearchSearchIndexRebuildAdapter(
            PostIndexManager indexManager,
            ElasticsearchPostSearchRepository postSearchRepository,
            SearchReindexTargetRegistry targetRegistry
    ) {
        this(indexManager, postSearchRepository, targetRegistry, newTargetRenewer(), true);
    }

    ElasticsearchSearchIndexRebuildAdapter(
            PostIndexManager indexManager,
            ElasticsearchPostSearchRepository postSearchRepository,
            SearchReindexTargetRegistry targetRegistry,
            ScheduledExecutorService targetRenewer,
            boolean ownsTargetRenewer
    ) {
        this.indexManager = indexManager;
        this.postSearchRepository = postSearchRepository;
        this.targetRegistry = targetRegistry;
        this.targetRenewer = targetRenewer;
        this.ownsTargetRenewer = ownsTargetRenewer;
    }

    @Override
    public RebuildSession begin(Duration visibilityTtl) {
        String indexName = indexManager.createNewIndex();
        boolean activated;
        try {
            activated = targetRegistry.activate(indexName, visibilityTtl);
        } catch (RuntimeException uncertainActivation) {
            log.warn(
                    "[search-reindex] preserving index {} because target registration outcome is unknown",
                    indexName,
                    uncertainActivation
            );
            throw uncertainActivation;
        }
        if (!activated) {
            IllegalStateException registrationFailure =
                    new IllegalStateException("search reindex target could not be registered");
            try {
                indexManager.deleteIndex(indexName);
            } catch (RuntimeException cleanupFailure) {
                registrationFailure.addSuppressed(cleanupFailure);
            }
            throw registrationFailure;
        }
        try {
            return new ElasticsearchRebuildSession(indexName, visibilityTtl);
        } catch (RuntimeException schedulingFailure) {
            if (!deactivateConfirmed(indexName, schedulingFailure)) {
                throw schedulingFailure;
            }
            try {
                indexManager.deleteIndex(indexName);
            } catch (RuntimeException cleanupFailure) {
                schedulingFailure.addSuppressed(cleanupFailure);
            }
            throw schedulingFailure;
        }
    }

    @Override
    public void cleanupRetiredIndices() {
        indexManager.cleanupOldIndices();
    }

    @PreDestroy
    void shutdown() {
        if (ownsTargetRenewer) {
            targetRenewer.shutdownNow();
        }
    }

    private boolean deactivateConfirmed(String indexName, RuntimeException primaryFailure) {
        try {
            targetRegistry.deactivate(indexName);
            return true;
        } catch (RuntimeException uncertainDeactivation) {
            if (primaryFailure != null && primaryFailure != uncertainDeactivation) {
                primaryFailure.addSuppressed(uncertainDeactivation);
            }
            log.warn(
                    "[search-reindex] preserving index {} because target removal outcome is unknown",
                    indexName,
                    uncertainDeactivation
            );
            return false;
        }
    }

    private final class ElasticsearchRebuildSession implements RebuildSession {

        private final String indexName;
        private final Duration visibilityTtl;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicBoolean valid = new AtomicBoolean(true);
        private final ScheduledFuture<?> renewal;
        private boolean published;
        private boolean publishAttempted;

        private ElasticsearchRebuildSession(String indexName, Duration visibilityTtl) {
            this.indexName = indexName;
            this.visibilityTtl = visibilityTtl;
            long renewalIntervalMs = Math.max(1L, visibilityTtl.dividedBy(3).toMillis());
            this.renewal = targetRenewer.scheduleAtFixedRate(
                    this::refreshTargetLease,
                    renewalIntervalMs,
                    renewalIntervalMs,
                    TimeUnit.MILLISECONDS
            );
        }

        @Override
        public void appendPage(List<PostSearchDocument> documents) {
            requireValid();
            List<PostSearchDocument> safeDocuments = List.copyOf(
                    Objects.requireNonNull(documents, "documents must not be null")
            );
            if (safeDocuments.size() > MAX_PAGE_SIZE) {
                throw new IllegalArgumentException("search reindex page must not exceed " + MAX_PAGE_SIZE + " documents");
            }
            if (!safeDocuments.isEmpty()) {
                postSearchRepository.saveAllToIndex(safeDocuments, indexName);
            }
        }

        @Override
        public boolean isValid() {
            return !closed.get() && valid.get();
        }

        @Override
        public void publish() {
            requireValid();
            indexManager.refreshIndex(indexName);
            if (!targetRegistry.refresh(indexName, visibilityTtl)) {
                valid.set(false);
                throw new IllegalStateException("search reindex target lease was lost before publication");
            }
            publishAttempted = true;
            indexManager.switchAliasTo(indexName);
            published = true;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            valid.set(false);
            renewal.cancel(false);
            if (!deactivateConfirmed(indexName, null)) {
                return;
            }
            if (published || publishAttempted) {
                return;
            }
            try {
                indexManager.deleteIndex(indexName);
            } catch (RuntimeException cleanupFailure) {
                log.warn("[search-reindex] failed to delete unpublished index {}", indexName, cleanupFailure);
            }
        }

        private void requireValid() {
            if (!isValid()) {
                throw new IllegalStateException("search reindex target lease is not valid");
            }
        }

        private void refreshTargetLease() {
            if (!closed.get() && valid.get() && !targetRegistry.refresh(indexName, visibilityTtl)) {
                valid.set(false);
            }
        }
    }

    private static ScheduledExecutorService newTargetRenewer() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "search-reindex-target-renewer");
            thread.setDaemon(true);
            return thread;
        });
    }
}
