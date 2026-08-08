package com.nowcoder.community.content.application;

import java.util.List;
import java.util.UUID;

public interface BookmarkCounterReconciliationPort {

    void recordMutation(UUID postId);

    List<PendingBookmarkCounter> listPending(int limit);

    boolean clearIfRevision(UUID postId, long revision);

    boolean deferIfRevision(UUID postId, long revision);

    record PendingBookmarkCounter(UUID postId, long revision) {
    }
}
