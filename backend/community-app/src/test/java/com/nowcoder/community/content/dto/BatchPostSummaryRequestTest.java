package com.nowcoder.community.content.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class BatchPostSummaryRequestTest {

    @Test
    void shouldStoreSubmittedPostIds() {
        List<UUID> postIds = List.of(uuid(3), uuid(5), uuid(8));
        BatchPostSummaryRequest request = new BatchPostSummaryRequest();
        request.setPostIds(postIds);

        assertThat(request.getPostIds()).containsExactlyElementsOf(postIds);
    }
}
