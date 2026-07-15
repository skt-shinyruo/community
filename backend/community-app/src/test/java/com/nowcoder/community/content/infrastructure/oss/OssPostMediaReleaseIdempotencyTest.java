package com.nowcoder.community.content.infrastructure.oss;

import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaAssetLifecycle;
import com.nowcoder.community.content.domain.model.PostMediaKind;
import com.nowcoder.community.content.domain.model.PostMediaReferenceStatus;
import com.nowcoder.community.content.domain.model.PostVideoState;
import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.model.OssReferenceResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OssPostMediaReleaseIdempotencyTest {

    private static final UUID OBJECT_ID = uuid(811);
    private static final UUID REFERENCE_ID = uuid(812);
    private static final UUID ACTOR_ID = uuid(7);

    @Test
    void releaseShouldTreatMissingRemoteReferenceAsAlreadyReleased() {
        CommunityOssClient client = mock(CommunityOssClient.class);
        when(client.getObjectReference(OBJECT_ID, REFERENCE_ID)).thenReturn(null);
        OssPostMediaStorageAdapter adapter = new OssPostMediaStorageAdapter(client);

        adapter.releaseReference(releasePendingAsset(), ACTOR_ID);

        verify(client, never()).releaseObjectReference(OBJECT_ID, REFERENCE_ID, ACTOR_ID.toString());
    }

    @Test
    void releaseShouldDeleteAnActiveRemoteReference() {
        CommunityOssClient client = mock(CommunityOssClient.class);
        when(client.getObjectReference(OBJECT_ID, REFERENCE_ID)).thenReturn(activeReference());
        OssPostMediaStorageAdapter adapter = new OssPostMediaStorageAdapter(client);

        adapter.releaseReference(releasePendingAsset(), ACTOR_ID);

        verify(client).releaseObjectReference(OBJECT_ID, REFERENCE_ID, ACTOR_ID.toString());
    }

    private PostMediaAsset releasePendingAsset() {
        Date now = Date.from(Instant.parse("2026-07-15T11:45:00Z"));
        return new PostMediaAsset(
                uuid(813),
                ACTOR_ID,
                uuid(814),
                OBJECT_ID,
                uuid(815),
                REFERENCE_ID,
                null,
                "fixture.png",
                "image/png",
                64L,
                PostMediaKind.IMAGE,
                PostMediaAssetLifecycle.UPLOADED,
                PostMediaReferenceStatus.RELEASE_PENDING,
                2L,
                now,
                PostVideoState.NONE,
                "https://cdn.example.com/fixture.png",
                "",
                now,
                now
        );
    }

    private OssReferenceResponse activeReference() {
        return new OssReferenceResponse(
                REFERENCE_ID,
                OBJECT_ID,
                uuid(815),
                "community-app",
                "content",
                "post",
                uuid(814).toString(),
                "POST_MEDIA",
                "ACTIVE",
                null,
                Instant.parse("2026-07-15T11:40:00Z"),
                null
        );
    }
}
