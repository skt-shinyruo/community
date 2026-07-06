package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.command.RecordPostViewCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostCounterApplicationServiceTest {

    private PostCounterCache postCounterCache;
    private PostCounterApplicationService service;

    @BeforeEach
    void setUp() {
        postCounterCache = mock(PostCounterCache.class);
        service = new PostCounterApplicationService(postCounterCache);
    }

    @Test
    void recordViewShouldDeduplicateWithinViewerWindow() {
        UUID postId = uuid(300);
        Instant viewedAt = Instant.parse("2026-07-06T10:00:00Z");
        RecordPostViewCommand command = new RecordPostViewCommand(postId, "viewer:aaa", viewedAt);
        when(postCounterCache.markViewerSeen(postId, "viewer:aaa", viewedAt)).thenReturn(true, false);

        service.recordView(command);
        service.recordView(command);

        verify(postCounterCache, times(1)).incrementViewCount(postId);
    }
}
