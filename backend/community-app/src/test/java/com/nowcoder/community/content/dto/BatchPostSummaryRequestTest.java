package com.nowcoder.community.content.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BatchPostSummaryRequestTest {

    @Test
    void shouldStoreSubmittedPostIds() {
        BatchPostSummaryRequest request = new BatchPostSummaryRequest();
        request.setPostIds(List.of(3, 5, 8));

        assertThat(request.getPostIds()).containsExactly(3, 5, 8);
    }
}
