package com.nowcoder.community.search.application;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.search.application.command.ReindexPostsCommand;
import com.nowcoder.community.search.application.result.SearchReindexResult;
import com.nowcoder.community.search.config.PostScanProperties;
import com.nowcoder.community.search.domain.model.PostSearchDocument;
import com.nowcoder.community.search.domain.repository.PostSearchIndexRepository;
import com.nowcoder.community.search.domain.repository.PostSearchRepository;
import com.nowcoder.community.search.domain.service.SearchReindexDomainService;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
public class SearchReindexApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SearchReindexApplicationService.class);
    private static final String CATEGORY_ASYNC = "async";
    private static final String MDC_CATEGORY = "community.category";
    private static final String MDC_ACTION = "community.action";
    private static final String MDC_OUTCOME = "community.outcome";

    private final PostSearchRepository postSearchRepository;
    private final PostScanQueryApi postScanQueryApi;
    private final ObjectProvider<PostSearchIndexRepository> postSearchIndexRepositoryProvider;
    private final ReindexJobApplicationService reindexJobApplicationService;
    private final SearchReindexDomainService searchReindexDomainService;
    private final int scanPageSize;

    public SearchReindexApplicationService(
            PostSearchRepository postSearchRepository,
            PostScanQueryApi postScanQueryApi,
            PostScanProperties properties,
            ObjectProvider<PostSearchIndexRepository> postSearchIndexRepositoryProvider,
            ReindexJobApplicationService reindexJobApplicationService,
            SearchReindexDomainService searchReindexDomainService
    ) {
        this.postSearchRepository = postSearchRepository;
        this.postScanQueryApi = postScanQueryApi;
        this.postSearchIndexRepositoryProvider = postSearchIndexRepositoryProvider;
        this.reindexJobApplicationService = reindexJobApplicationService;
        this.searchReindexDomainService = searchReindexDomainService;
        this.scanPageSize = searchReindexDomainService.normalizeScanPageSize(properties.getPageSize());
    }

    public SearchReindexResult reindex(ReindexPostsCommand command) {
        ReindexJobApplicationService.ReindexJob job = reindexJobApplicationService.tryStart();
        if (job == null || !job.acquired()) {
            infoEvent(
                    "search_reindex",
                    "skipped",
                    "community.reason_code", "already_running",
                    "community.job_id", job == null ? null : job.jobId()
            );
            return new SearchReindexResult(
                    job == null ? null : job.jobId(),
                    0,
                    true,
                    searchReindexDomainService.skippedReason(job == null ? null : job.jobId())
            );
        }

        infoEvent(
                "search_reindex_start",
                "success",
                "community.job_id", job.jobId()
        );
        try (ReindexJobApplicationService.RenewalHandle ignored = reindexJobApplicationService.startRenewal(job)) {
            try {
                int count = clearAndReindexFromContentService();
                infoEvent(
                        "search_reindex",
                        "success",
                        "community.job_id", job.jobId(),
                        "community.indexed_count", count
                );
                return new SearchReindexResult(job.jobId(), count, false, null);
            } catch (RuntimeException e) {
                warnEvent(
                        "search_reindex",
                        "failure",
                        e,
                        "community.reason_code", "reindex_failed",
                        "community.job_id", job.jobId()
                );
                throw e;
            }
        } finally {
            reindexJobApplicationService.finish(job);
        }
    }

    int clearAndReindexFromContentService() {
        PostSearchIndexRepository indexRepository = postSearchIndexRepositoryProvider.getIfAvailable();
        String targetIndex = null;
        if (indexRepository != null) {
            indexRepository.ensureAliasReady();
            targetIndex = indexRepository.createNewIndex();
        } else {
            postSearchRepository.clear();
        }

        int total = 0;
        UUID afterId = null;

        while (true) {
            PostScanView page = postScanQueryApi.scanPosts(afterId, scanPageSize);
            if (page == null || page.items().isEmpty()) {
                break;
            }

            for (PostScanView.PostProjectionView projection : page.items()) {
                PostSearchDocument document = PostSearchPayloadMapper.toDocument(projection);
                if (targetIndex == null) {
                    postSearchRepository.save(document);
                } else {
                    postSearchRepository.saveToIndex(document, targetIndex);
                }
                total++;
            }

            UUID nextAfterId = page.nextAfterId();
            if (nextAfterId == null || (afterId != null && nextAfterId.compareTo(afterId) <= 0)) {
                break;
            }
            afterId = nextAfterId;

            if (!page.hasMore()) {
                break;
            }
        }

        if (indexRepository != null && targetIndex != null) {
            indexRepository.switchAliasTo(targetIndex);
            indexRepository.cleanupOldIndices();
        }

        return total;
    }

    private void infoEvent(String action, String outcome, Object... keyValues) {
        logEvent(action, outcome, false, null, keyValues);
    }

    private void warnEvent(String action, String outcome, Throwable throwable, Object... keyValues) {
        logEvent(action, outcome, true, throwable, keyValues);
    }

    private void logEvent(String action, String outcome, boolean warn, Throwable throwable, Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Search reindex event keyValues must contain key/value pairs");
        }
        String previousCategory = MDC.get(MDC_CATEGORY);
        String previousAction = MDC.get(MDC_ACTION);
        String previousOutcome = MDC.get(MDC_OUTCOME);
        MDC.put(MDC_CATEGORY, CATEGORY_ASYNC);
        MDC.put(MDC_ACTION, action);
        MDC.put(MDC_OUTCOME, outcome);
        try {
            String message = buildMessage(action, outcome, keyValues);
            if (warn) {
                if (throwable == null) {
                    log.warn(message);
                } else {
                    log.warn(message, throwable);
                }
                return;
            }
            log.info(message);
        } finally {
            restore(MDC_CATEGORY, previousCategory);
            restore(MDC_ACTION, previousAction);
            restore(MDC_OUTCOME, previousOutcome);
        }
    }

    private String buildMessage(String action, String outcome, Object... keyValues) {
        StringBuilder message = new StringBuilder(160);
        appendToken(message, MDC_CATEGORY, CATEGORY_ASYNC);
        appendToken(message, MDC_ACTION, action);
        appendToken(message, MDC_OUTCOME, outcome);
        for (int i = 0; i < keyValues.length; i += 2) {
            appendToken(message, String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return message.toString();
    }

    private void appendToken(StringBuilder message, String key, Object value) {
        if (message.length() > 0) {
            message.append(' ');
        }
        message.append(key).append('=').append(encodeTokenValue(value));
    }

    private String encodeTokenValue(Object value) {
        if (value == null) {
            return "-";
        }
        String raw = String.valueOf(value);
        if (raw.isEmpty()) {
            return "-";
        }
        StringBuilder encoded = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch) || ch == '=' || ch == '%') {
                encoded.append('%');
                String hex = Integer.toHexString(ch).toUpperCase(Locale.ROOT);
                if (hex.length() == 1) {
                    encoded.append('0');
                }
                encoded.append(hex);
            } else {
                encoded.append(ch);
            }
        }
        return encoded.toString();
    }

    private void restore(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, previousValue);
    }
}
