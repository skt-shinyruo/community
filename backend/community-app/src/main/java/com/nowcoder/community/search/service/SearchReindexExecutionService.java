package com.nowcoder.community.search.service;

import com.nowcoder.community.search.api.action.SearchReindexActionApi;
import com.nowcoder.community.search.api.model.SearchReindexResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class SearchReindexExecutionService implements SearchReindexActionApi {

    private static final Logger log = LoggerFactory.getLogger(SearchReindexExecutionService.class);
    private static final String CATEGORY_ASYNC = "async";
    private static final String MDC_CATEGORY = "community.category";
    private static final String MDC_ACTION = "community.action";
    private static final String MDC_OUTCOME = "community.outcome";

    private final PostSearchService postSearchService;
    private final ReindexJobService reindexJobService;

    public SearchReindexExecutionService(PostSearchService postSearchService, ReindexJobService reindexJobService) {
        this.postSearchService = postSearchService;
        this.reindexJobService = reindexJobService;
    }

    @Override
    public SearchReindexResult reindex() {
        ReindexJobService.ReindexJob job = reindexJobService.tryStart();
        if (job == null || !job.acquired()) {
            infoEvent(
                    "search_reindex",
                    "skipped",
                    "community.reason_code", "already_running",
                    "community.job_id", job == null ? null : job.jobId()
            );
            return new SearchReindexResult(job == null ? null : job.jobId(), 0, true, skippedReason(job));
        }

        infoEvent(
                "search_reindex_start",
                "success",
                "community.job_id", job.jobId()
        );
        try {
            int count = postSearchService.clearAndReindexFromContentService();
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
        } finally {
            reindexJobService.finish(job);
        }
    }

    private String skippedReason(ReindexJobService.ReindexJob job) {
        String jobId = job == null ? null : job.jobId();
        String suffix = StringUtils.hasText(jobId) ? " (jobId=" + jobId.trim() + ")" : "";
        return "reindex 任务正在执行" + suffix;
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
