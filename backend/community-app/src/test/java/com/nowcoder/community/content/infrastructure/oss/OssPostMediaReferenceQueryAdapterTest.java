package com.nowcoder.community.content.infrastructure.oss;

import com.nowcoder.community.content.application.PostMediaReferenceQueryPort;
import com.nowcoder.community.content.application.PostMediaReferenceQueryPort.RemoteReferenceStatus;
import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.model.OssReferenceResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OssPostMediaReferenceQueryAdapterTest {

    private static final UUID OBJECT_ID = uuid(801);
    private static final UUID REFERENCE_ID = uuid(802);

    @Test
    void adapterShouldImplementApplicationOwnedQueryPort() {
        assertThat(OssPostMediaReferenceQueryAdapter.class.getInterfaces())
                .containsExactly(PostMediaReferenceQueryPort.class);
    }

    @Test
    void activeAndReleasedOssStatesShouldMapWithoutLeakingClientModels() {
        CommunityOssClient client = mock(CommunityOssClient.class);
        OssPostMediaReferenceQueryAdapter adapter = new OssPostMediaReferenceQueryAdapter(client);
        when(client.getObjectReference(OBJECT_ID, REFERENCE_ID))
                .thenReturn(response("ACTIVE"), response("RELEASED"));

        assertThat(adapter.findReferenceStatus(OBJECT_ID, REFERENCE_ID))
                .isEqualTo(RemoteReferenceStatus.ACTIVE);
        assertThat(adapter.findReferenceStatus(OBJECT_ID, REFERENCE_ID))
                .isEqualTo(RemoteReferenceStatus.RELEASED);
    }

    @Test
    void missingAndUnrecognizedOssStatesShouldRemainDistinct() {
        CommunityOssClient client = mock(CommunityOssClient.class);
        OssPostMediaReferenceQueryAdapter adapter = new OssPostMediaReferenceQueryAdapter(client);
        when(client.getObjectReference(OBJECT_ID, REFERENCE_ID))
                .thenReturn(null, response("CORRUPT"));

        assertThat(adapter.findReferenceStatus(OBJECT_ID, REFERENCE_ID))
                .isEqualTo(RemoteReferenceStatus.MISSING);
        assertThat(adapter.findReferenceStatus(OBJECT_ID, REFERENCE_ID))
                .isEqualTo(RemoteReferenceStatus.UNKNOWN);
    }

    private OssReferenceResponse response(String status) {
        return new OssReferenceResponse(
                REFERENCE_ID,
                OBJECT_ID,
                uuid(803),
                "community-app",
                "content",
                "post",
                uuid(804).toString(),
                "POST_MEDIA",
                status,
                null,
                Instant.parse("2026-07-15T11:30:00Z"),
                "RELEASED".equals(status) ? Instant.parse("2026-07-15T11:31:00Z") : null
        );
    }
}
