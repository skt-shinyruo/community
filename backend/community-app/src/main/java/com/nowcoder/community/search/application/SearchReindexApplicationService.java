package com.nowcoder.community.search.application;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.search.domain.model.PostSearchDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "search.storage", havingValue = "es")
public class SearchReindexApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SearchReindexApplicationService.class);
    private static final int DEFAULT_PAGE_SIZE = 500;
    private static final int MAX_PAGE_SIZE = 1000;
    private static final Duration DEFAULT_LEASE_TTL = Duration.ofMinutes(30);
    private static final Duration MIN_LEASE_TTL = Duration.ofSeconds(3);

    private final PostScanQueryApi postScanQueryApi;
    private final SearchIndexRebuildPort indexRebuildPort;
    private final SearchReindexLeasePort reindexLeasePort;
    private final SearchPolicyProperties searchPolicyProperties;
    private final int pageSize;
    private final Duration leaseTtl;

    @Autowired
    public SearchReindexApplicationService(
            PostScanQueryApi postScanQueryApi,
            SearchIndexRebuildPort indexRebuildPort,
            SearchReindexLeasePort reindexLeasePort,
            @Value("${search.reindex.page-size:500}") int pageSize,
            @Value("${search.reindex.lock-ttl:30m}") Duration leaseTtl,
            SearchPolicyProperties searchPolicyProperties
    ) {
        this.postScanQueryApi = postScanQueryApi;
        this.indexRebuildPort = indexRebuildPort;
        this.reindexLeasePort = reindexLeasePort;
        this.searchPolicyProperties = Objects.requireNonNull(
                searchPolicyProperties, "searchPolicyProperties must not be null"
        );
        this.pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize));
        this.leaseTtl = normalizeLeaseTtl(leaseTtl);
    }

    public ReindexResult reindex() {
        if (!searchPolicyProperties.isProjectionEnabled()) {
            throw new IllegalStateException("online search projection must be enabled during a full reindex");
        }
        String executionId = UUID.randomUUID().toString();
        Optional<SearchReindexLeasePort.Lease> acquired = reindexLeasePort.tryAcquire(leaseTtl);
        if (acquired.isEmpty()) {
            log.info("[search-reindex] skipped executionId={} reason=already-running-or-lock-unavailable", executionId);
            return new ReindexResult(executionId, 0L, true, "already running or lock unavailable");
        }

        SearchReindexLeasePort.Lease lease = acquired.orElseThrow();
        try (lease) {
            assertLeaseValid(lease);
            long indexedCount = rebuildFromCurrentContentFacts(lease);
            try {
                indexRebuildPort.cleanupRetiredIndices();
            } catch (RuntimeException cleanupFailure) {
                log.warn("[search-reindex] retired-index cleanup failed executionId={}", executionId, cleanupFailure);
            }
            log.info("[search-reindex] completed executionId={} indexedCount={}", executionId, indexedCount);
            return new ReindexResult(executionId, indexedCount, false, null);
        } catch (RuntimeException failure) {
            log.warn("[search-reindex] failed executionId={}", executionId, failure);
            throw failure;
        }
    }

    private long rebuildFromCurrentContentFacts(SearchReindexLeasePort.Lease lease) {
        long total = 0L;
        UUID afterId = null;

        try (SearchIndexRebuildPort.RebuildSession session = indexRebuildPort.begin(leaseTtl)) {
            while (true) {
                assertHealthy(lease, session);
                PostScanView page = Objects.requireNonNull(
                        postScanQueryApi.scanPosts(afterId, pageSize),
                        "content post scan returned null"
                );
                List<PostScanView.PostProjectionView> items = page.items();
                if (items.isEmpty()) {
                    if (page.hasMore()) {
                        throw new IllegalStateException("content post scan returned an empty page with hasMore=true");
                    }
                    break;
                }
                if (items.size() > pageSize) {
                    throw new IllegalStateException("content post scan exceeded the requested page size");
                }

                UUID previousItemId = afterId;
                List<PostSearchDocument> documents = new ArrayList<>(items.size());
                for (PostScanView.PostProjectionView projection : items) {
                    assertHealthy(lease, session);
                    UUID postId = Objects.requireNonNull(
                            Objects.requireNonNull(projection, "content post scan returned a null item").postId(),
                            "content post scan returned an item without postId"
                    );
                    if (previousItemId != null && compareUuidBytes(postId, previousItemId) <= 0) {
                        throw new IllegalStateException("content post scan items were not ordered by the binary UUID cursor");
                    }
                    PostSearchDocument document = PostSearchPayloadAssembler.toDocument(
                            projection
                    );
                    documents.add(document);
                    previousItemId = postId;
                }

                UUID nextAfterId = page.nextAfterId();
                if (nextAfterId == null
                        || !nextAfterId.equals(previousItemId)
                        || (afterId != null && compareUuidBytes(nextAfterId, afterId) <= 0)) {
                    throw new IllegalStateException("content post scan cursor did not advance");
                }
                assertHealthy(lease, session);
                session.appendPage(List.copyOf(documents));
                total += documents.size();
                if (!page.hasMore()) {
                    break;
                }
                afterId = nextAfterId;
            }

            assertHealthy(lease, session);
            session.publish();
        }
        return total;
    }

    private void assertHealthy(
            SearchReindexLeasePort.Lease lease,
            SearchIndexRebuildPort.RebuildSession session
    ) {
        assertLeaseValid(lease);
        if (!session.isValid()) {
            throw new IllegalStateException("search reindex target lease was lost");
        }
    }

    private void assertLeaseValid(SearchReindexLeasePort.Lease lease) {
        if (lease == null || !lease.isValid()) {
            throw new IllegalStateException("search reindex single-flight lease was lost");
        }
    }

    private Duration normalizeLeaseTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return DEFAULT_LEASE_TTL;
        }
        return ttl.compareTo(MIN_LEASE_TTL) < 0 ? MIN_LEASE_TTL : ttl;
    }

    /** UUID.compareTo uses signed longs and does not implement the owner API's canonical byte order. */
    private int compareUuidBytes(UUID left, UUID right) {
        int mostSignificant = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return mostSignificant != 0
                ? mostSignificant
                : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }

    public record ReindexResult(
            String executionId,
            long indexedCount,
            boolean skipped,
            String reason
    ) {
    }
}
