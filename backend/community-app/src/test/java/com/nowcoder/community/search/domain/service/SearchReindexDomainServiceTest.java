package com.nowcoder.community.search.domain.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchReindexDomainServiceTest {

    private final SearchReindexDomainService service = new SearchReindexDomainService();

    @Test
    void normalizeScanPageSizeShouldClampSupportedRange() {
        assertThat(service.normalizeScanPageSize(-1)).isEqualTo(1);
        assertThat(service.normalizeScanPageSize(0)).isEqualTo(1);
        assertThat(service.normalizeScanPageSize(1001)).isEqualTo(1000);
        assertThat(service.normalizeScanPageSize(200)).isEqualTo(200);
    }

    @Test
    void skippedReasonShouldIncludeJobIdWhenPresent() {
        assertThat(service.skippedReason("job-1")).contains("job-1");
        assertThat(service.skippedReason(null)).isEqualTo("reindex 任务正在执行");
    }
}
